# Final project acceptance evidence

This tracks verified outcomes through 18 October 2026. A checked local module
does not imply live GitHub or Azure acceptance. Use synthetic fixtures and
record run/commit IDs, commands, expected and actual results; keep credentials,
raw reports, tokens, and recipient addresses out of evidence.

| Outcome | Current evidence | Remaining acceptance |
| --- | --- | --- |
| Backend foundation and migrations | M00/M01 local setup; disposable PostgreSQL runner; V1–V9 migrations; 147-test full suite passed on 18 October (4 opt-in real-scanner tests skipped) | Teammate fresh-clone/second-machine proof and deferred GitHub collaboration |
| Authentication and roles | M02/M03 registration, login, JWT, admin and cross-owner negative tests | Cloud login/session check after M15 |
| Repository, scans, and findings | M04–M07 owner-scoped APIs, CI replay semantics, safe normalized findings, local clean/finding scanner fixtures | Cloud CI record tied to a GitHub run and exact SHA |
| Incident, audit, gate | M08/M09 lifecycle, history, blocked decisions and backend tests; M11 local browser walkthrough | Cloud scan-to-incident/audit/gate walkthrough |
| High-risk notifications | M10 outbox and fake-provider success/failure/retry tests with synthetic data | M10.4 real ACS sender, approved recipient, actual success and failure/retry evidence |
| Dashboard | M11 React pages, frontend tests, local browser screenshots and live disposable-backend walkthrough | Cloud login, filters, actions, and responsive checks |
| GitHub scanning/gate | M12 clean `main` push and finding/error PR run links in [workflow guide](github-workflow.md); four targeted local reporter tests; protected `main` requires both CI checks; PR #11 and merged-main run 39142518443 passed, with release/publish/deploy jobs skipped | Fork behavior, trusted push reporting/backend match, successful image publication/deployment, and a new unsafe run proving deployment remains skipped |
| Local containers | M13 backend/frontend images, PostgreSQL Compose stack, proxy, fixture scans and persistence verified in [Docker guide](docker-setup.md) | Teammate fresh-clone reproduction (M13.4) |
| Azure infrastructure | Verified USD 20 monthly budget, protected state account, approved [18-resource application apply](m14-plan-review.md), [actual inventory and diagram](azure-architecture-actual.md), HTTPS placeholder URLs, four resolved Key Vault references, three mocked plan tests | Real app runtime, Azure login/API, and ACS delivery verification in M15/M10.4 |
| Cloud deployment/operations | Guarded publish/deploy workflow and dry-run setup helpers prepared; `azure-dev` is `main`-only with reviewed OIDC identity variables; both images built in merged-main CI; [operations guide](deployment-guide.md) drafted | Public exact-SHA GHCR images, live OIDC deployment, cloud application checks, rollback, backup restore, logging and redaction checks |
| Final report/demo | This matrix, [technical report draft](technical-report.md), [demo script](demo-script.md), and [slide outline](presentation-outline.md) provide source material | Actual cloud results, sanitized screenshots/recordings, full rehearsal, reviewed final merge/tag |

The 18 October PostgreSQL suite executed 147 tests with zero failures or errors;
four real-scanner tests are opt-in. Frontend Vitest passed six tests in three
files. Earlier M11 browser screenshots are in
`docs/evidence/`. These are local or synthetic where stated. The cloud website
is not deployed. On 18 October, the user rechecked both Azure application URLs:
each returned HTTP 200 but did not contain Secret Shield health/content. Azure
application infrastructure is therefore live with placeholder pages;
the original Terraform creation plan must not be applied again.
