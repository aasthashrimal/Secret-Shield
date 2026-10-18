# Secret Shield demonstration script (draft)

Use only synthetic repositories/accounts and sanitized screenshots. Record the
exact commit SHA, GitHub run URL, backend scan ID, and displayed status before
the presentation. A live cloud section requires the separate M14/M15 approvals
and completed checks in [remaining-prerequisites.md](remaining-prerequisites.md).

| Scene | Action | Expected evidence | Current status |
| --- | --- | --- | --- |
| 1. Scope | Show architecture, local/CI/cloud status and budget | Proposed vs actual Azure resources clearly separated | Draft ready; final resource diagram pending |
| 2. Login | Sign in with a synthetic owner account | Dashboard loads; another owner's data is inaccessible | Local tests/walkthrough; cloud pending |
| 3. Clean scan | Trigger/show a clean fixture and trusted main CI run | `CLEAN`, same SHA in GitHub and backend, eligible gate | Local fixture and earlier GitHub gate; backend reporting pending |
| 4. Synthetic leak | Show a synthetic credential fixture in a disposable PR | `FINDINGS_DETECTED`; build/publish/deploy skipped | Prior PR #7 gate evidence; new publish/deploy skip proof pending |
| 5. Scanner error | Show a bounded error fixture | `FAILED`; build/publish/deploy skipped | Prior PR #8 gate evidence; new skip proof pending |
| 6. Investigation | Open a finding, change an incident, view audit/history | Owner-visible transition and immutable event | Local tests/dashboard; cloud pending |
| 7. High-risk alert | Trigger an approved synthetic high-risk event | Outbox status, real ACS delivery and failure/retry evidence | Fake-provider verified; real delivery pending |
| 8. Azure delivery | Show exact SHA image, live URL, Key Vault/identity and health | Deployed commit matches clean reported workflow | Pending separate approvals and M15 deployment |
| 9. Operations | Explain backup, rollback and outage fallback | Restore/rollback test, sanitized logs, cost/retention decision | Guide drafted; live rehearsal pending |

For each scene, keep a fallback recording or screenshot after the real check
passes. Do not use a recording as proof of a check that has not happened.
If Azure is unavailable during the presentation, show the local Compose app
and accurately label the cloud section as unavailable. Never expose tokens,
database passwords, raw scanner output, ACS keys, or recipient addresses on
screen. Slides can follow the nine scenes above; finalize them with actual
evidence and rehearse with the teammate before checking M16.4/M16.5.
