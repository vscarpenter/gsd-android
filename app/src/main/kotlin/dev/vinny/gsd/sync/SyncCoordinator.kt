package dev.vinny.gsd.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import dev.vinny.gsd.auth.CachedTokenStore
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.prefs.PendingSignIn
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.SyncOp
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.first
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
    /** In noBackupFilesDir, the one place a phone-to-phone transfer never copies (see [ensureOwnDeviceId]). */
    private val deviceMarker: File = File(context.noBackupFilesDir, "device-identity"),
) {
    private val foreground = MutableStateFlow(false)
    private val syncRequests = MutableSharedFlow<Boolean>(extraBufferCapacity = 16)

    val syncing = MutableStateFlow(false)
    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()
    val pendingCount = db.syncQueueDao().observePendingCount()
    val failedCount = db.syncQueueDao().observeFailedCount()
    val lastResult = MutableStateFlow<SyncResult?>(null)

    fun start() {
        scope.launch {
            ensureOwnDeviceId() // first: sync and realtime only begin once this launch signs in, below
            tokens.warm()
            val userId = auth.currentUserId()
            val previous = lastOwnerId()
            if (userId != null && previous != userId) {
                // The app died before it recorded this owner: the token is saved, the owner is not. The
                // pending sign-in keeps the email, or Settings would show none until the next sign-in (review S3).
                val email = prefs.pendingSignIn.first()?.takeIf { it.id == userId }?.email
                val unfinished = AuthResult(tokens.token().orEmpty(), AuthRecord(userId, email.orEmpty()))
                if (previous != null && local.activeTasks().isNotEmpty()) {
                    // A different account with tasks to decide about. Ask again and stay signed out,
                    // so nothing syncs before the user decides (spec §8.21).
                    switchPrompt.value = unfinished
                    return@launch
                }
                // Nothing to ask: this is the first owner, or no tasks are left. Finish the change before
                // any sync. adopt clears a cursor the old account left behind. Recording the owner matters
                // even for the first one, or a later account switch finds no previous owner and never asks.
                OwnerChange.adopt(local, previous, userId, keepLocalTasks = false, nowMs = System.currentTimeMillis())
                onOwnerConfirmed(unfinished)
            }
            _signedIn.value = userId != null
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
                    val result = engine.runSync(user)
                    lastResult.value = result
                    if (result.notSignedIn) {
                        // The server rejected the token, or a refresh already cleared it: the session is over
                        // (spec §10.3). Forget the token too, or a restart would sign back in to a dead
                        // session (review S1). Expired first, so whoever sees "signed out" also sees why.
                        auth.signOut()
                        _sessionExpired.value = true
                        _signedIn.value = false
                    }
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
            is SseDecision.ApplyUpsert -> applyRealtimeUpsert(local, event.record)
            is SseDecision.ApplyDelete -> local.deleteFromRemote(event.record.taskId)
            is SseDecision.Drop -> Unit
        }
    }

    /**
     * Gives this install its own device id before anything reads it (spec §9.8). Without the marker,
     * the data is new or came from another phone, which keeps the old id. Sharing it, each phone
     * dropped the other's realtime events as echoes until the next pull (review finding 42).
     */
    private suspend fun ensureOwnDeviceId() {
        if (deviceMarker.exists()) return
        db.syncMetadataDao().get()?.let { row ->
            db.syncMetadataDao().upsert(row.copy(deviceId = UUID.randomUUID().toString()))
        }
        deviceMarker.parentFile?.mkdirs()
        deviceMarker.createNewFile()
    }

    /** Set when a sign-in resolves to a different owner with local tasks present (spec §8.21). */
    val switchPrompt = MutableStateFlow<AuthResult?>(null)

    var lastOwnerId: suspend () -> String? = { null }
    var onOwnerConfirmed: suspend (AuthResult) -> Unit = {}

    suspend fun completeOAuthCallback(uri: String): AuthResult {
        val result = auth.completeOAuth(uri)
        // Saved before the decision, so a prompt rebuilt after a restart still knows the email and provider
        prefs.setPendingSignIn(PendingSignIn(result.record.id, result.record.email.ifBlank { null }, auth.lastProvider))
        val previous = lastOwnerId()
        val hasLocalTasks = local.activeTasks().isNotEmpty()
        if (previous != null && previous != result.record.id && hasLocalTasks) {
            switchPrompt.value = result // decision deferred to the UI
        } else {
            confirmOwner(result, eraseLocalFirst = false)
        }
        return result
    }

    /**
     * "Keep my tasks" or "Start fresh" (§8.21). A different owner clears the pull cursor, and
     * kept tasks are seeded into the queue once so reconciliation protects them until they upload.
     * The new owner is recorded last, so [start] can tell when the app died partway. With tasks
     * left it raises the prompt again. With none left it finishes the change itself.
     */
    suspend fun confirmOwner(result: AuthResult, eraseLocalFirst: Boolean) {
        val previous = lastOwnerId()
        if (eraseLocalFirst) dev.vinny.gsd.store.LocalErase.eraseAll(db)
        OwnerChange.adopt(
            local, previous, result.record.id,
            keepLocalTasks = !eraseLocalFirst, nowMs = System.currentTimeMillis(),
        )
        onOwnerConfirmed(result)
        prefs.clearPendingSignIn()
        switchPrompt.value = null
        _sessionExpired.value = false
        _signedIn.value = true
        requestSync(true)
    }

    /**
     * The provider of [userId]'s sign-in. Held in memory until a restart, and in the pending sign-in
     * after one, so an owner confirmed on a rebuilt prompt still gets the right account note.
     */
    suspend fun providerFor(userId: String): String? =
        auth.lastProvider ?: prefs.pendingSignIn.first()?.takeIf { it.id == userId }?.provider

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
        } catch (cancelled: CancellationException) {
            throw cancelled
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                return DeleteAccountOutcome.LocalEraseIncomplete
            }
        }
        return outcome
    }
}
