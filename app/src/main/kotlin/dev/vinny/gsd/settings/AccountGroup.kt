package dev.vinny.gsd.settings

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.sync.DeleteAccountOutcome
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import kotlinx.coroutines.launch

/** Settings Account section (spec §8.17.3): signing in IS enabling sync — no toggle. */
@Composable
internal fun AccountGroup(container: AppContainer, onOpenHistory: () -> Unit) {
    val gsd = LocalGsdColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val signedIn by container.syncCoordinator.signedIn.collectAsStateWithLifecycle()
    val sessionExpired by container.syncCoordinator.sessionExpired.collectAsStateWithLifecycle()
    val pending by container.syncCoordinator.pendingCount.collectAsStateWithLifecycle(initialValue = 0)
    val failed by container.syncCoordinator.failedCount.collectAsStateWithLifecycle(initialValue = 0)
    val email by container.prefs.lastOwnerEmail.collectAsStateWithLifecycle(initialValue = null)
    val authProvider by container.prefs.lastAuthProvider.collectAsStateWithLifecycle(initialValue = null)
    var deleteOpen by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }
    val deletionMessage by container.accountDeletionMessage.collectAsStateWithLifecycle()

    fun deleteAccount(eraseLocal: Boolean) {
        deleteOpen = false
        // The app's scope, so a rotation cannot cancel a deletion midway or lose its outcome (finding 40)
        container.appScope.launch {
            container.accountDeletionMessage.value = when (
                container.syncCoordinator.deleteAccount(container.api, container.api, eraseLocal = eraseLocal)
            ) {
                DeleteAccountOutcome.Deleted -> null
                DeleteAccountOutcome.ListingFailed -> R.string.account_delete_listing_failed
                DeleteAccountOutcome.PartialAuthRejected -> R.string.account_delete_partial_auth
                DeleteAccountOutcome.PartialRetry -> R.string.account_delete_partial_retry
                DeleteAccountOutcome.LocalEraseIncomplete -> R.string.account_delete_local_incomplete
            }
        }
    }

    fun launchProvider(provider: String) {
        scope.launch {
            signInError = null
            container.accountDeletionMessage.value = null
            try {
                val pendingAuth = container.authManager.beginOAuth(provider)
                CustomTabsIntent.Builder().build().launchUrl(context, pendingAuth.url.toUri())
            } catch (failure: Exception) {
                android.util.Log.w("GsdAuth", "sign-in with $provider failed: ${failure.javaClass.simpleName}")
                signInError = context.getString(R.string.sign_in_error)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_account).uppercase(),
            style = GsdType.caption,
            color = gsd.ink3,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (!signedIn) {
            SignedOutAccount(sessionExpired, signInError) { launchProvider(it) }
        } else {
            email?.let { address ->
                Text(address, style = GsdType.body, color = gsd.ink)
                when (accountNote(authProvider, address)) {
                    AccountNote.RELAY_SEPARATE_ACCOUNT ->
                        Text(stringResource(R.string.account_note_relay), style = GsdType.metadata, color = gsd.ink3)
                    AccountNote.APPLE_MAY_DIFFER ->
                        Text(stringResource(R.string.account_note_apple), style = GsdType.metadata, color = gsd.ink3)
                    null -> Unit
                }
            }
            Text(
                stringResource(R.string.account_synced, pending),
                style = GsdType.metadata,
                color = gsd.ink2,
            )
            if (failed > 0) {
                Text(stringResource(R.string.health_failed), style = GsdType.metadata, color = gsd.ochre)
            }
            AccountAction(R.string.account_sync_now) { container.syncCoordinator.requestSync(true) }
            AccountAction(R.string.account_sync_history, onClick = onOpenHistory)
            AccountAction(R.string.account_sign_out) {
                container.appScope.launch { container.syncCoordinator.signOut() }
            }
            AccountAction(R.string.account_delete, tint = gsd.alert) { deleteOpen = true }
        }
        // Rendered outside the signed-in branch: a local-erase failure after a successful
        // remote delete flips signedIn to false before this recomposes, so the message must
        // survive that transition to stay visible.
        deletionMessage?.let { Text(stringResource(it), style = GsdType.subhead, color = gsd.alert) }
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text(stringResource(R.string.account_delete_title), style = GsdType.cardTitle) },
            text = { Text(stringResource(R.string.account_delete_body), style = GsdType.body) },
            confirmButton = {
                Column {
                    TextButton(onClick = { deleteAccount(eraseLocal = false) }) {
                        Text(stringResource(R.string.account_delete_keep))
                    }
                    TextButton(onClick = { deleteAccount(eraseLocal = true) }) {
                        Text(stringResource(R.string.account_delete_erase), color = gsd.alert)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

/** The signed-out Account rows. They emit straight into the caller's column, so its spacing applies. */
@Composable
internal fun SignedOutAccount(sessionExpired: Boolean, signInError: String?, onProvider: (String) -> Unit) {
    val gsd = LocalGsdColors.current
    if (sessionExpired) {
        // Ochre, not alert: nothing was lost, the user only needs to sign in again (spec §5.3)
        Text(stringResource(R.string.health_expired), style = GsdType.metadata, color = gsd.ochre)
    }
    AccountAction(R.string.sign_in_google) { onProvider("google") }
    AccountAction(R.string.sign_in_apple) { onProvider("apple") }
    AccountAction(R.string.sign_in_github) { onProvider("github") }
    signInError?.let { Text(it, style = GsdType.metadata, color = gsd.alert) }
    Text(stringResource(R.string.account_privacy_hint), style = GsdType.metadata, color = gsd.ink3)
}

@Composable
private fun AccountAction(labelRes: Int, tint: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val gsd = LocalGsdColors.current
    Text(
        stringResource(labelRes),
        style = GsdType.body,
        color = tint ?: gsd.tint,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}
