package dev.vinny.gsd.sync

import kotlinx.coroutines.delay

/**
 * Route-first account erasure with a bounded client-side fallback (S§8.4). Only a 404
 * on the route falls back — any other failure stops with nothing changed. The user
 * record is deleted only after a complete listing comes back empty; retrying is safe.
 */
class AccountDeleter(
    private val tasks: SyncRemote,
    private val account: AccountRemote,
    private val throttleMs: Long = 100,
) {
    private companion object { const val MAX_PASSES = 3; const val EPOCH = "1970-01-01 00:00:00.000Z" }

    suspend fun delete(userId: String): DeleteAccountOutcome {
        try {
            account.deleteAccountViaRoute()
            return DeleteAccountOutcome.Deleted
        } catch (absent: PocketBaseApi.HttpError) {
            if (absent.code != 404) return DeleteAccountOutcome.ListingFailed
        } catch (transport: Exception) {
            return DeleteAccountOutcome.ListingFailed
        }
        var erasedAny = false
        repeat(MAX_PASSES) {
            val listing = try {
                BoundedListing.collect(EPOCH, tasks::listTasks)
            } catch (failure: Exception) {
                return if (erasedAny) partial(failure) else DeleteAccountOutcome.ListingFailed
            }
            if (listing.isEmpty()) {
                return try { account.deleteUser(userId); DeleteAccountOutcome.Deleted } catch (failure: Exception) { partial(failure) }
            }
            for (wire in listing) {
                try { tasks.deleteTask(wire.id) } catch (failure: Exception) { return if (erasedAny) partial(failure) else DeleteAccountOutcome.ListingFailed }
                erasedAny = true
                if (throttleMs > 0) delay(throttleMs)
            }
        }
        return DeleteAccountOutcome.PartialRetry
    }

    private fun partial(failure: Exception): DeleteAccountOutcome =
        if (failure is PocketBaseApi.Unauthorized) DeleteAccountOutcome.PartialAuthRejected else DeleteAccountOutcome.PartialRetry
}
