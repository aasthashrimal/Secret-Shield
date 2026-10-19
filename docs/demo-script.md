# Secret Shield demonstration script

Use synthetic accounts and repositories. Keep developer tools closed and never
show tokens, connection strings, passwords, raw reports, recipient addresses,
Terraform state, or Key Vault values.

| Scene | Live action | Expected result | Fallback |
| --- | --- | --- | --- |
| 1. Scope | Show the [actual architecture](azure-architecture-actual.md) | Explain Spring, React, Neon, GitHub Actions, Azure, Key Vault and ACS | `m15-azure-resources.png` |
| 2. Login | Sign in with the synthetic owner | HTTPS dashboard loads | `m11-login.png`, `m15-live-dashboard.png` |
| 3. Clean delivery | Open run `39247034941` | Scan/build/release/publish/deploy pass for `8e36c800...`; bootstrap skips | `final-clean-deploy-run.png` |
| 4. Finding | Open run `39245401264`, then repository 5/scan 9 | Gate fails; downstream jobs skip; one LOW sanitized finding appears | `m12-finding-blocked-run.png`, `m12-finding-dashboard.png` |
| 5. Scanner error | Open run `39056726283` | Gate fails closed and downstream work skips | `m12-scanner-error-run.png` |
| 6. Investigation | Open repository 4/incident 2 | HIGH incident is RESOLVED with creation/resolution history; gate stays blocked | `m15-notification-incident.png` |
| 7. Alert | Show incident 2 notifications | `SENT`, attempts 1; no recipient/body displayed | `m15-notification-incident.png` |
| 8. Azure | Show resource group and live dashboard | Reviewed resources exist and app works over HTTPS | `m15-azure-resources.png`, `m15-live-dashboard.png` |
| 9. Operations | Explain logs, restore, rollback and teardown | Recovery/retention decisions are clear without destructive action | [Deployment guide](deployment-guide.md) |

## Rehearsal record

On 19 October 2026 the owner completed login, overview, scan 9, incident 2 with
notification/history, logout/relogin, final GitHub run, and Azure resource group.
All seven checks passed. Deployment identity was run `39247034941`, commit
`8e36c800eecfb338bca68c4654393b37db0eff03`.

If Azure is unavailable, use the reviewed screenshots and local Compose. State
that cloud service is temporarily unavailable; do not present fallback media as
a new live check.
