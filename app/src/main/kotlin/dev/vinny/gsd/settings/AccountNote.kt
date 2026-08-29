package dev.vinny.gsd.settings

/** Two-tier Apple sign-in note (parity spec P§7) — relay wins over provider. */
enum class AccountNote { RELAY_SEPARATE_ACCOUNT, APPLE_MAY_DIFFER }

fun accountNote(provider: String?, email: String?): AccountNote? = when {
    email != null && email.endsWith("@privaterelay.appleid.com") -> AccountNote.RELAY_SEPARATE_ACCOUNT
    provider == "apple" -> AccountNote.APPLE_MAY_DIFFER
    else -> null
}
