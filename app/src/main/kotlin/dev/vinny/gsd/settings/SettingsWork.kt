package dev.vinny.gsd.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.model.Interchange
import dev.vinny.gsd.palette.OneShotRequest
import dev.vinny.gsd.store.BackupAssembler
import dev.vinny.gsd.store.ImportApplier
import dev.vinny.gsd.store.ImportMode
import dev.vinny.gsd.store.LocalErase
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The Settings work that must finish once it starts (review finding 40). Callers run it in the app's
 * scope, so a rotation or a trip to another tab cannot cancel it midway. Each piece reports what the
 * user needs to know through a one-shot message, which Settings shows the next time it is on screen.
 * Failures are logged by exception class only, because a message can carry task text (spec §13.4).
 */

private const val TAG = "GsdSettings"

/**
 * Writes the backup to [uri]. Mode "wt" truncates. A provider may take plain "w" as an overwrite in
 * place, and a shorter export written over a longer file then kept a stale tail (review finding 39).
 */
internal suspend fun exportBackup(
    assembler: BackupAssembler,
    messages: OneShotRequest<String>,
    context: Context,
    uri: Uri,
) {
    try {
        val json = Interchange.export(assembler.assemble(), Instant.now())
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("no output stream")
            stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w(TAG, "export failed: ${error.javaClass.simpleName}")
        messages.send(context.getString(R.string.export_failed))
    }
}

/**
 * Reads, parses, and applies the file off the main thread, and reports the counts or a plain reason.
 * The applier writes in one transaction, so a failure leaves nothing half imported (review finding 39).
 */
internal suspend fun importBackup(
    applier: ImportApplier,
    messages: OneShotRequest<String>,
    context: Context,
    uri: Uri,
    mode: ImportMode,
) {
    val message = try {
        val raw = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw IOException("no input stream")
        }
        val summary = applier.apply(Interchange.parse(raw), mode)
        context.getString(R.string.import_result, summary.imported, summary.skipped)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Interchange.TooLarge) {
        context.getString(R.string.import_too_large)
    } catch (error: Exception) {
        Log.w(TAG, "import failed: ${error.javaClass.simpleName}")
        context.getString(R.string.import_failed)
    }
    messages.send(message)
}

/**
 * Signed in means erase everywhere: the remote records first, and the local wipe only after that
 * succeeds (spec §8.16, §9.8). A remote failure used to end the flow in silence.
 */
internal suspend fun eraseEverything(container: AppContainer, context: Context) {
    if (container.syncCoordinator.signedIn.value) {
        try {
            container.syncCoordinator.eraseRemote(container.api)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "remote erase failed: ${error.javaClass.simpleName}")
            container.settingsMessages.send(context.getString(R.string.erase_remote_failed))
            return
        }
    }
    LocalErase.eraseAll(container.db)
    container.prefs.clearFeedbackDraft()
    container.prefs.clearSweepState()
}
