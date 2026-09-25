# Play Data Safety form — answers (spec §14)

Accurate to the code as of 2026-09-24 (review finding 17). The v0.7.0 answers predated the feedback
form. A local-only user sends nothing unless they sign in to sync or choose to send feedback, and
the app shares nothing with third parties.

**Owner step:** compare the Play Console's Data safety form with this page. The form was filed on
2026-07-19, before feedback shipped, so it may lack the feedback row below. The privacy page at
gsdtaskmanager.com/privacy/ lives in the site's own repo. The 2026-09-20 review read its source and
found it names only iPhone, iPad, and Mac. It should also mention Android, the anonymous feedback
form, and the no-cloud-backup rule.

## Data collection & sharing
| Question | Answer |
|---|---|
| Does the app collect or share user data? | **Collects, only with optional sync sign-in or an optional feedback submission. Shares: no.** |
| Data types collected (sync) | **Personal info → Email address** (account identity from the OAuth provider). **App activity → Other user-generated content** (task titles, notes, tags, dates). |
| Data types collected (feedback) | **App activity → Other user-generated content**: the message the user types, a thumbs up or down, a category, and roadmap votes. The payload also carries the app version, a random submission id, and the phone's clock time. It carries no account, no email, and no device id, and it is sent with no auth header. The server keeps it 180 days. |
| Is collection optional? | **Yes** — the app is fully functional without an account; sync is opt-in, and feedback is sent only when the user taps Send. |
| Purpose | Email: app functionality + account management. Tasks: app functionality (multi-device sync). Feedback: analytics, meaning product feedback to the developer (confirm the category in the Console). |
| Encrypted in transit? | **Yes** (HTTPS only). |
| Can users request deletion? | **Yes** — in-app account deletion (Settings → Account → Delete Account) removes every synced record and the user record. Web deletion URL as filed (both "delete account" and "delete data"): https://gsdtaskmanager.com/privacy/ (its "Keeping and deleting your data" section). A dedicated deletion page can replace it later by editing the Data safety form. |
| Shared with third parties? | **No.** Self-hosted backend (api.vinny.io), no ads, no analytics SDKs, no tracking. |
| Backups | **None to the cloud.** `allowBackup="false"` plus data extraction rules that exclude every domain from cloud backup (finding 16). A phone-to-phone transfer the user starts moves their tasks and settings to their new phone, without the sign-in token. Nothing reaches the developer or Google, so it is not collection. |
| Anonymous feedback deletion | Feedback carries no account link, so it cannot be tied to a user for deletion. The server deletes it after 180 days. |

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
