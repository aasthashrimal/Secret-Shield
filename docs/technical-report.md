# Secret Shield technical report (draft)

**Status:** Local, GitHub CI, and Azure infrastructure evidence through 18
October 2026. Application deployment, cloud acceptance, screenshots, and final
teammate review are pending.

## Problem and scope

Secret Shield detects exposed credentials in repository history, blocks unsafe
delivery, and gives owners a place to review findings and incidents. It has a
Spring Boot API, PostgreSQL/Flyway data layer, React dashboard, a pinned
Gitleaks scanner, and a GitHub Actions gate. Scans distinguish `CLEAN`,
`FINDINGS_DETECTED`, and `FAILED`; scanner failure does not pass the gate.

## Architecture and modules

Authentication uses JWT, owner-scoped repository APIs, and role checks. Scan
records carry branch/commit identities. Findings and incident transitions
preserve audit history; high-risk events enter a retryable notification outbox.
The dashboard uses a same-origin `/api` proxy in its container. Local Docker
Compose runs frontend, backend and disposable PostgreSQL. See
[azure-architecture-actual.md](azure-architecture-actual.md) for the applied
Azure topology, [cloud-design.md](cloud-design.md) for design alternatives, and
[data-model.md](data-model.md) for the database model.

M00–M11 implement the local foundation, domain/API workflow and dashboard.
M12 has a scan/gate workflow with local reporting tests and prior live GitHub
clean/finding/error runs; full reporting/deployment evidence is pending. M13
has a locally tested Compose stack. M14 has validated Terraform and an approved,
applied 18-resource infrastructure plan with placeholder pages. M15 contains guarded publish/deploy automation and
dry-run setup helpers, but no live application deployment yet.

## Security and delivery choices

The scanner uses a pinned image, read-only checkout, restricted container and
redacted normalized report. PR jobs do not receive Azure deployment identity.
Trusted main pushes need a clean scan, successful build, and matching backend
report before publication. Optional publish/deploy flags are disabled until
separately activated. The Azure apps use a Key Vault for DB/JWT/ACS
secrets, Neon JDBC TLS, and environment-scoped GitHub OIDC. The approved M14 plan
created 18 resources in two passes; Azure PostgreSQL and ACR are
optional and excluded. See [deployment-guide.md](deployment-guide.md).

## Verified results

- On 18 October, the disposable PostgreSQL suite passed 147 tests with zero
  failures/errors; four opt-in real-scanner tests were skipped.
- Frontend Vitest passed 6 tests in 3 files. Local Node reporting tests passed
  4 targeted negative/retry cases. Both backend and frontend Docker images
  built locally.
- The earlier GitHub clean main push, synthetic finding, and scanner-error
  runs are linked with identities in [github-workflow.md](github-workflow.md).
- PR #11 passed both protected checks and merged as `95dc4a3`. Merged-main run
  `39142518443` passed the scan, backend/frontend checks, and both container
  builds. Reporting to the placeholder backend failed safely; release candidate,
  bootstrap publication, normal publication, and Azure deployment were skipped.
- Terraform formatting, validation, and three mocked plan tests passed for
  M14. The USD 20/month alert budget, protected state storage, and all 18
  approved application resources now exist. A user-run HTTP check on 18 October
  returned 200 from both URLs but found no Secret Shield health/content,
  confirming that both still serve placeholders.
  Four approved Neon/JWT secret versions were uploaded; Azure reports all four
  backend references `Resolved` without disclosing their values.
- A [partial deliverable security audit](security-audit.md) classified local
  scanner matches without publishing matched values. Image, cloud-log, and
  browser-output review still need the final deployed build.

These results establish local behavior and earlier CI gate outcomes. They do
not establish a live Azure login to Secret Shield, notification delivery,
image publish, or deployed Secret Shield website.

## Pending results and limitations

Complete the [acceptance matrix](acceptance-checklist.md) and
[prerequisite list](remaining-prerequisites.md) after separately approved cloud
actions. Capture actual run ID/SHA, backend scan ID, deployed image SHA,
dashboard/incident/audit outcome, ACS delivery status, backup restore and
rollback result. Sanitize evidence before publication. The student credit
display expires 15 November 2026; a permanent website link depends on renewed
credit or another funded hosting choice. The Azure budget alerts but does not
automatically stop spending.

## Future work

After final acceptance, review operational cost and a long-term hosting plan,
automate backup verification, and add observability only where it improves
incident response. Do not claim these as current functionality.
