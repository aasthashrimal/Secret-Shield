# Final project acceptance evidence

This is a checklist for the complete synopsis, not a claim that later modules
exist. Member 1 maintains Modules 0–11 alone; GitHub collaboration is deferred.
Use only synthetic secret fixtures. Record tool/version, commit, commands, expected
and actual outcomes, and sanitized evidence; omit secrets, tokens and raw reports.

| Outcome | Required final evidence | Current status |
| --- | --- | --- |
| Reproducible backend foundation | Java 21-targeted compile/start, unit suite, isolated PostgreSQL suite, fresh/legacy migration preservation | M00–M04; 57 database-independent and 99 full tests verified |
| Authentication and roles | Registration/login/me, invalid/expired tokens, developer/admin negative access tests | M02/M03 verified; resource-specific ownership suites extend this in M04+ |
| Repository and scan management | Two-user ownership, validated metadata, scan transitions, idempotent CI result ingestion | M04–M06 verified; current full suite has 117 executed tests plus four opt-in real-scanner tests |
| Credential detection | Pinned real Gitleaks runs for clean/synthetic-secret/error fixtures; safe normalized outputs | M06 local detection and M07 persistence/sanitization verified |
| Severity and findings | Deterministic mapping/fallback, retained occurrences, authorized filters, no raw secret fields | M07 verified; current full suite has 123 executed tests plus four opt-in scanner tests |
| Automated GitHub scanning | Push/PR starts scanner for the correct branch/commit; authenticated sanitized submission | M12 workflow code and local scanner tests; live GitHub runs, fork checks, and backend record evidence pending |
| Security gate | Clean exact commit allowed; finding/error/timeout/missing result blocks; pipeline cannot bypass on API outage | M09 backend implemented; M12 pipeline pending |
| Dashboard | Live authorized repository/scan/finding/incident views, filters, empty/loading/error states | M11 implemented; disposable browser walkthrough and synthetic screenshots in `docs/evidence/` |
| Incident lifecycle | OPEN/RESOLVED/IGNORED reasons, concurrency checks, recurrence/reopening, review does not bypass gate | M08 verified; M11 browser resolution and blocked-gate check |
| Audit and history | Append-only typed actor/repository events, ordered authorized views, no confidential payloads | M08 verified; M11 timeline presentation |
| High-risk notifications | Azure Communication Services synthetic HIGH/CRITICAL alert, safe recipient configuration, retries/dedup, retained delivery status | M10 local implementation and fake-provider checks; live Azure evidence M10.4 pending M14 |
| Containers | Reproducible image/full-stack startup, migrations, restart persistence, no secrets copied to image | M13 pending |
| Azure infrastructure | Reviewed Terraform plan/state protection, documented resources and HTTPS access | M14/M15 pending |
| Deployment and operations | Correct image/commit deployed, readiness/configuration, alerts/log redaction, rollback evidence | M15 pending |
| Final demonstration/report | Clean run plus blocked synthetic-secret run, dashboard/history/notification proof, limitations documented | M16 pending |

GitHub settings and second-machine reproduction require their own evidence when
resumed; they are not prerequisites for the currently authorized solo foundation work.
