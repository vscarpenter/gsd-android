# Security policy

GSD Android is a solo project, and I read every security report myself. Please report vulnerabilities privately so I can fix them before they become public.

## Report a vulnerability

Don't open a public issue. [Open a private security advisory](https://github.com/vscarpenter/gsd-android/security/advisories/new) instead, and include:

- What the vulnerability is and what an attacker could do with it
- Steps to reproduce, with your app and Android versions
- A suggested fix, if you have one

I try to reply within 7 days and will tell you what I plan to do about the report. This project has no bug bounty.

## Supported versions

I fix security bugs in the latest release and on `main`. I don't patch older releases, so please update before you report.

## Where reports belong

This repo covers the Android app, including token storage, sign-in, the sync client, deep links, the share sheet, and backup import.

The hosted sync server at `api.vinny.io` and its configuration live in the web app's repo, so report server issues [there](https://github.com/vscarpenter/gsd-task-manager/security/advisories/new). If you aren't sure which repo fits, report here and I'll sort it out.

## Testing rules

Test with your own account or your own PocketBase server. Don't access or change anyone else's data, and don't run load tests or automated scanners against `api.vinny.io`.

## Known behavior

These are deliberate choices, so they don't need a report:

- The hosted sync server stores task content unencrypted at rest. TLS protects it in transit, owner-scoped API rules separate accounts, and I can technically read it as the operator. Sync is optional, and [ADR 0016](https://github.com/vscarpenter/gsd-task-manager/blob/main/docs/adr/0016-hosted-sync-defers-field-encryption.md) explains the decision.
- The feedback form posts without an account, so it carries no sign-in token or task content.
- Another app can register the `gsd://` scheme and receive a sign-in callback. PKCE stops that app from exchanging the code, because the verifier stays in this app's memory.
