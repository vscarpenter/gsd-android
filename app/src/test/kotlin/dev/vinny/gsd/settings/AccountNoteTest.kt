package dev.vinny.gsd.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Two-tier Apple sign-in note (parity spec P§7): relay wins over provider. */
class AccountNoteTest {
    @Test
    fun `relay email wins regardless of provider`() {
        assertEquals(
            AccountNote.RELAY_SEPARATE_ACCOUNT,
            accountNote(provider = "apple", email = "abc123@privaterelay.appleid.com"),
        )
        assertEquals(
            AccountNote.RELAY_SEPARATE_ACCOUNT,
            accountNote(provider = "google", email = "abc123@privaterelay.appleid.com"),
        )
        assertEquals(
            AccountNote.RELAY_SEPARATE_ACCOUNT,
            accountNote(provider = null, email = "abc123@privaterelay.appleid.com"),
        )
    }

    @Test
    fun `apple provider with a normal email warns without relay wording`() {
        assertEquals(AccountNote.APPLE_MAY_DIFFER, accountNote(provider = "apple", email = "v@x.dev"))
    }

    @Test
    fun `google or github with a normal email is silent`() {
        assertNull(accountNote(provider = "google", email = "v@x.dev"))
        assertNull(accountNote(provider = "github", email = "v@x.dev"))
    }

    @Test
    fun `no persisted provider and a normal email is silent`() {
        assertNull(accountNote(provider = null, email = "v@x.dev"))
    }

    @Test
    fun `no provider and no email is silent`() {
        assertNull(accountNote(provider = null, email = null))
    }

    @Test
    fun `apple provider with no email still warns`() {
        assertEquals(AccountNote.APPLE_MAY_DIFFER, accountNote(provider = "apple", email = null))
    }
}
