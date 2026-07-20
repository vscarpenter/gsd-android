# Play Data Safety form — answers (spec §14)

Accurate to shipped behavior as of v0.7.0. Local-only users: the app collects nothing and shares
nothing. Everything below the "with optional sync" line applies only after explicit sign-in.

## Data collection & sharing
| Question | Answer |
|---|---|
| Does the app collect or share user data? | **Collects, only with optional sync sign-in. Shares: no.** |
| Data types collected (sync only) | **Personal info → Email address** (account identity from the OAuth provider). **App activity → Other user-generated content** (task titles, notes, tags, dates). |
| Is collection optional? | **Yes** — the app is fully functional without an account; sync is opt-in. |
| Purpose | Email: app functionality + account management. Tasks: app functionality (multi-device sync). |
| Encrypted in transit? | **Yes** (HTTPS only). |
| Can users request deletion? | **Yes** — in-app account deletion (Settings → Account → Delete Account) removes every synced record and the user record. Web deletion URL as filed (both "delete account" and "delete data"): https://gsdtaskmanager.com/privacy/ (its "Keeping and deleting your data" section). A dedicated deletion page can replace it later by editing the Data safety form. |
| Shared with third parties? | **No.** Self-hosted backend (api.vinny.io), no ads, no analytics SDKs, no tracking. |

## Permission declarations
| Permission | Declaration text |
|---|---|
| `SCHEDULE_EXACT_ALARM` | Exact alarms deliver user-scheduled task reminders at the precise time the user chose. Reminders are the app's core alerting feature and inexact delivery would make them late. |
| `POST_NOTIFICATIONS` | Task reminder notifications, requested contextually. |
| `RECEIVE_BOOT_COMPLETED` | Reschedules the user's task reminders after a reboot. |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Optional account sync with the user's own data backend. |

## Account deletion (Play requirement)
- In-app path: Settings → Account → Delete Account… (erases remote records, then the user record, then local token). ✔ shipped
- Web path: filed as https://gsdtaskmanager.com/privacy/ (covers deletion steps and retention). ✔ form completed 2026-07-19. Optional follow-up: publish a dedicated /delete-account/ page and swap the URL in the form.
