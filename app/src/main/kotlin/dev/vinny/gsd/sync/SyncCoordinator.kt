package dev.vinny.gsd.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import dev.vinny.gsd.auth.CachedTokenStore
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.SyncOp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The trigger fabric around the SyncEngine (spec §9.5–§9.6): launch,
 * foreground, network-regained, 120 s cadence, 1.5 s debounced mutation push,
 * manual Sync Now — plus the foreground-only SSE stream with reconnect
 * backoff and a full sync after every drop.
 */
@OptIn(FlowPreview::class)
class SyncCoordinator(
    private val context: Context,
    private val engine: SyncEngine,
    private val auth: AuthManager,
    private val tokens: CachedTokenStore,
    private val db: GsdDatabase,
    private val local: SyncLocalStore,
    private val sse: SseClient,
    private val scope: CoroutineScope,
    private val prefs: GsdPrefs,
) {
    private val foreground = MutableStateFlow(false)
    private val syncRequests = MutableSharedFlow<Boolean>(extraBufferCapacity = 16)

    val syncing = MutableStateFlow(false)
    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()
    val pendingCount = db.syncQueueDao().observePendingCount()
    val failedCount = db.syncQueueDao().observeFailedCount()
    val lastResult = MutableStateFlow<SyncResult?>(null)

    fun start() {
        scope.launch {
            tokens.warm()
            _signedIn.value = auth.currentUserId() != null
            if (_signedIn.value) {
                engine.requeueFailed() // launch trigger requeues failed items (spec §9.5)
                requestSync(false)
            }
        }
        scope.launch {
            syncRequests.collect { user ->
                if (!_signedIn.value) return@collect
                syncing.value = true
                try {
                    auth.ensureFreshToken()
                    lastResult.value = engine.runSync(user)
                } finally {
                    syncing.value = false
                }
            }
        }
        scope.launch {
            // 1.5 s debounced push after local mutations (spec §9.6)
            db.syncQueueDao().observePendingCount().debounce(1_500).collect { count ->
                if (count > 0) requestSync(false)
            }
        }
        scope.launch {
            // 120 s cadence safety net while active (spec §9.6)
            foreground.collectLatest { active ->
                while (active && scope.isActive) {
                    delay(120_000)
                    requestSync(false)
                }
            }
        }
        scope.launch {
            // SSE runs foreground-only; every drop → full sync + backoff reconnect
            combine(foreground, _signedIn) { fg, si -> fg && si }.collectLatest { active ->
                var backoffMs = 1_000L
                while (active && scope.isActive) {
                    val healthy = try {
                        sse.run { event -> applyRealtime(event) }
                        true
                    } catch (_: Exception) {
                        false
                    }
                    if (healthy) backoffMs = 1_000L
                    requestSync(false)
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000)
                }
            }
        }
        context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch {
                        if (_signedIn.value) {
                            engine.requeueFailed()
                            requestSync(false)
                        }
                    }
                }
            },
        )
    }

    fun onForeground() {
        foreground.value = true
        scope.launch {
            if (_signedIn.value) {
                engine.requeueFailed()
                requestSync(false)
            }
        }
    }

    fun onBackground() {
        foreground.value = false
    }

    fun requestSync(user: Boolean) {
        if (user) scope.launch { engine.requeueFailed() } // manual sync requeues (spec §9.5)
        syncRequests.tryEmit(user)
    }

    private suspend fun applyRealtime(event: SseClient.TaskEvent) {
        val ownUser = auth.currentUserId() ?: return
        val ownDevice = local.deviceId()
        val pending = local.pendingQueue()
        val failed = local.failedQueue()
        val decision = SseApplyRules.decide(
            action = event.action,
            record = event.record,
            ownUserId = ownUser,
            ownDeviceId = ownDevice,
            pendingDeleteTaskIds = pending.filter { it.operation == SyncOp.DELETE }.map { it.taskId }.toSet(),
            pendingAnyTaskIds = (pending + failed).map { it.taskId }.toSet(),
        )
        when (decision) {
            is SseDecision.ApplyUpsert -> {
                val existing = local.taskById(event.record.taskId)
                val incoming = WireMapper.pull(event.record, existing) ?: return
                if (!resolveTombstone(local, incoming.id, incoming.updatedAt.toEpochMilli())) {
                    return // archived/trashed locally, remote not strictly newer (parity spec P§4)
                }
                if (existing == null ||
                    incoming.updatedAt.toEpochMilli() > existing.updatedAt.toEpochMilli()
                ) {
                    local.upsertFromRemote(incoming)
                }
            }
            is SseDecision.ApplyDelete -> local.deleteFromRemote(event.record.taskId)
            is SseDecision.Drop -> Unit
        }
    }

    /** Set when a sign-in resolves to a different owner with local tasks present (spec §8.21). */
    val switchPrompt = MutableStateFlow<AuthResult?>(null)

    var lastOwnerId: suspend () -> String? = { null }
    var onOwnerConfirmed: suspend (AuthResult) -> Unit = {}

    suspend fun completeOAuthCallback(uri: String): AuthResult {
        val result = auth.completeOAuth(uri)
        val previous = lastOwnerId()
        val hasLocalTasks = local.activeTasks().isNotEmpty()
        if (previous != null && previous != result.record.id && hasLocalTasks) {
            switchPrompt.value = result // decision deferred to the UI
        } else {
            confirmOwner(result, eraseLocalFirst = false)
        }
        return result
    }

    /** "Keep my tasks" (merge — the queue seed-uploads) or "Start fresh" (§8.21). */
    suspend fun confirmOwner(result: AuthResult, eraseLocalFirst: Boolean) {
        if (eraseLocalFirst) dev.vinny.gsd.store.LocalErase.eraseAll(db)
        onOwnerConfirmed(result)
        switchPrompt.value = null
        _signedIn.value = true
        requestSync(true)
    }

    /** Cancel on the switch prompt signs back out (spec §8.21). */
    suspend fun cancelSwitch() {
        switchPrompt.value = null
        signOut()
    }

    suspend fun signOut() {
        auth.signOut()
        _signedIn.value = false
    }

    /**
     * Erase every remote record (spec §9.8): fresh index, DELETE each with the
     * ~100 ms throttle, clear the queue only after ALL deletes succeed. A dead
     * session throws so callers refuse to wipe local data on a false success.
     */
    suspend fun eraseRemote(remote: SyncRemote) {
        if (!_signedIn.value) return // signed-out is a silent no-op
        auth.ensureFreshToken()
        if (auth.currentUserId() == null) throw PocketBaseApi.Unauthorized(401)
        val recordIds = BoundedListing.collect("1970-01-01 00:00:00.000Z", remote::listTasks).map { it.id }
        recordIds.forEach { recordId ->
            remote.deleteTask(recordId)
            delay(100)
        }
        db.syncQueueDao().clear()
    }

    /**
     * §8.4/§8.21 order: server route first, bounded fallback, then the token.
     * Never throws: a missing user id, a failed token refresh, or any failure inside
     * [AccountDeleter.delete] means nothing changed, so it reports [DeleteAccountOutcome.ListingFailed]
     * rather than letting the caller mistake a pre-flight failure for a completed erase.
     */
    suspend fun deleteAccount(remote: SyncRemote, account: AccountRemote, eraseLocal: Boolean): DeleteAccountOutcome {
        val userId = auth.currentUserId() ?: return DeleteAccountOutcome.ListingFailed
        val outcome = try {
            auth.ensureFreshToken()
            AccountDeleter(remote, account).delete(userId)
        } catch (failure: Exception) {
            return DeleteAccountOutcome.ListingFailed
        }
        if (outcome != DeleteAccountOutcome.Deleted) return outcome
        db.syncQueueDao().clear()
        auth.signOut()
        _signedIn.value = false
        if (eraseLocal) {
            try {
                dev.vinny.gsd.store.LocalErase.eraseAll(db)
                prefs.clearFeedbackDraft()
            } catch (failure: Exception) {
                return DeleteAccountOutcome.LocalEraseIncomplete
            }
        }
        return outcome
    }
}
