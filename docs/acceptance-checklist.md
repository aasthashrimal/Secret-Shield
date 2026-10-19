# Final project acceptance evidence

This matrix records verified results through 19 October 2026. Evidence uses
synthetic fixtures and sanitized screenshots; credentials, raw scanner reports,
tokens, recipient addresses, and Terraform state are excluded.

| Outcome | Verified evidence | Honest limitation |
| --- | --- | --- |
| Backend and migrations | Flyway V1-V9; 147-test disposable PostgreSQL suite passed | Independent second-machine reproduction is deferred |
| Authentication and roles | Registration, login, logout, JWT, admin and cross-owner negative tests; live browser session passed | Public registration remains enabled for this demonstration deployment |
| Repositories, scans and findings | Owner-scoped APIs, local scanner fixtures, and sanitized live scans 8/9 | Private disposable evidence repository is retained for review |
| Incidents, audit and gate | Live incident 2 creation/resolution/history and blocked gate; automated authorization tests | Resolving an incident intentionally does not unblock a finding-bearing commit |
| Notifications | Live HIGH event verified bounded failure/retry and later one-attempt ACS `SENT` | Delivery depends on ACS and the private recipient mapping |
| Dashboard | Frontend tests/build and live HTTPS login/dashboard/logout/relogin | Focused academic dashboard rather than a provider installation product |
| GitHub gate | [Clean/finding/error evidence](github-workflow.md), trusted reporting, exact-SHA publication and blocked downstream jobs | Forks scan without backend/cloud secrets and do not claim ingestion |
| Local containers | Images, Compose stack, proxy, fixture scans and persistence verified in the [Docker guide](docker-setup.md) | Teammate fresh-clone reproduction remains M13.4 |
| Azure infrastructure | Approved 18-resource apply, protected state, Key Vault references, HTTPS and [actual diagram](azure-architecture-actual.md) | Neon is the separately managed PostgreSQL provider |
| Cloud operations | Run `39247034941` deployed SHA `8e36c800eecfb338bca68c4654393b37db0eff03`; health, logs, backup restore and procedures verified | Destructive restore, rollback and teardown were correctly not executed |
| Security audit | [Final audit](security-audit.md) covers Git, files, images, logs, browser evidence, Terraform artifacts and revocation | Historical scanner result is one deliberate removed synthetic fixture |
| Demonstration | Seven-step live rehearsal passed; sanitized fallbacks are in `docs/evidence/` | Final release tag follows the documentation PR |

Final verification combines the 147-test PostgreSQL suite, six frontend tests,
five workflow/reporting tests, live browser checks and successful main run
`39247034941`. No later functional change invalidated those results.

The remaining non-release gaps require another person: collaborator workflow
and clean-clone/second-machine reproduction. They remain explicitly deferred.
