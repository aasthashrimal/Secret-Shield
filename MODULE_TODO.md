# Secret Shield — Module Todo and Team Work Split

Based on both project PDFs and the repository review dated 11 October 2026. Read [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) for scope, current implementation, architecture, and proposed data model.

**Member 1:** you. **Member 2:** your teammate.

**Current scope override:** Member 1 alone implements all Module 0/1/2/3 setup, documentation and backend tasks. M00.2/M00.3 GitHub collaboration is explicitly deferred. Second-machine reproduction is outside this solo scope and remains unverified. Later-module assignments stay as planned.

## How to use this checklist

Every unchecked item is future work. Existing code is described in the overview; a task is checked only after its completion evidence exists. Step IDs are stable so you can ask for one step at a time. Unless stated otherwise, steps depend on earlier steps in their module. Dependencies on other modules are stated under each module.

Suggested prompt:

> Implement M02.1 from MODULE_TODO.md. Read its dependencies and PROJECT_OVERVIEW.md first. Explain the changes, verify the acceptance criteria, and update only the steps actually completed. Do not start later steps.

For a teammate task:

> Implement M11.4 for Member 2 using the contracts and fixtures from M11.1. Keep changes within the assigned page/component files. Verify the page states and update the checklist.

For each step: implement the named deliverable, run the relevant check, record a short verification note beside the checkbox, and make one focused pull request or include closely related steps in the same PR. Keep failed or environment-blocked checks explicit. Do not mark later implementation complete merely because documentation or a mock exists.

## Module index

| ID | Module | Main owner | Prerequisites |
| --- | --- | --- | --- |
| M00 | GitHub collaboration and reproducible setup | Member 1; documentation by Member 2 | None |
| M01 | Contracts, schema and backend foundation cleanup | Member 1 | M00 setup |
| M02 | Finish authentication | Member 1 | M01 |
| M03 | Roles and authorization | Member 1 | M02 |
| M04 | Repository management | Member 1 | M03 |
| M05 | Scan management and result ingestion | Member 1 | M04 |
| M06 | Local Gitleaks integration | Member 1 | M05 |
| M07 | Findings and severity | Member 1 | M06 |
| M08 | Incidents, audit and history | Member 1 | M07 |
| M09 | Security gate | Member 1 | M06–M08 |
| M10 | High-risk notifications | Member 1; documentation by Member 2 | M08 |
| M11 | Dashboard APIs and React dashboard | Both, with file ownership | M01 contracts; live integration after M08–M10 |
| M12 | GitHub Actions and automated scanning | Member 1; evidence by Member 2 | M09; M13 for image build |
| M13 | Docker and local full-stack execution | Member 1; setup guide by Member 2 | Runnable backend/frontend |
| M14 | Terraform and Azure infrastructure | Member 1; diagram by Member 2 | M13; local flow verified |
| M15 | Azure deployment and operations | Member 1; evidence by Member 2 | M12–M14 and M10 |
| M16 | Final testing, report and demonstration | Both | All required modules |

## M00 — GitHub collaboration and setup

Current state: Git is initialized and implementation files are tracked. GitHub hosting, collaborator access, and branch protections are deferred and have not been verified.

- [x] **M00.1 — Member 1:** Inspect the current branch/remotes and tracked files without printing credential-bearing URLs; expand `.gitignore` for `.env` files, local configuration, raw scanner outputs, logs, Terraform state/plans, and generated frontend files. Add sanitized environment examples with placeholders. **Done when:** intended source is tracked and no real credentials/generated secret-bearing files appear in the proposed commit. **Verified 11 October 2026:** inspected `main`, clean starting worktree, and `origin` fetch/push transport/host without printing URLs; expanded ignore rules and added `.env.example` covering all four backend variables with empty credential placeholders and an explicit process-environment loading note. Passed 32 ignore/template cases, 14 source/lockfile cases, and `git diff --check`; no tracked files match ignore rules. Reviewed 27 tracked/proposed files using credential-pattern checks and inspected backend configuration: no credential candidates or generated secret-bearing files found. This is a working-tree review, not a Gitleaks scan of Git history.
- [ ] **M00.2 — Member 1:** Create or connect the shared GitHub repository if needed, grant Member 2 access, and document the default branch and PR review process. Configure available branch protections to require PR review and CI checks. **Done when:** both members can clone/push feature branches and the actual protection settings or plan limitations are recorded.
- [ ] **M00.3 — Member 1:** Establish branch names (`m1/m02-auth`, `m2/m11-overview-page`), PR template, module ownership, and one issue per step or related step group. **Done when:** a sample PR lists step IDs, validation, changed areas, and reviewer.
- [x] **M00.4 — Member 1:** Reproduce backend setup with Java 21, the checked-in Maven wrapper, PostgreSQL, `DB_PASSWORD`, and `JWT_SECRET`; run compile/startup and the current test separately. Do not replace dependency versions merely to match a tutorial. **Done when:** working commands and any baseline failures are recorded without secrets. **Current completion evidence:** Verified 11 October 2026: Java 21.0.12.1 wrapper compile, separate 40-test unit run, 52-test disposable PostgreSQL 18.6 run, and real local startup passed. Seven live smoke checks passed on first startup and ordinary restart without adoption; temporary app and test cluster stopped. Dependency versions retained. Commands: docs/local-setup.md.
- [x] **M00.5 — Member 1 (solo scope):** Write `README.md` and `docs/local-setup.md` from M00.4 evidence, covering purpose, prerequisites, safe environment configuration on Windows, startup, endpoints, and troubleshooting. Follow the guide on this machine. **Done when:** local commands are verified and second-machine work is explicitly deferred under the current solo scope. **Current completion evidence:** Completed by Member 1 under solo scope: README.md and docs/local-setup.md cover prerequisites, private environment loading, fresh/existing databases, startup, routes, tests and troubleshooting. Commands verified on this Windows machine; second-machine execution is deferred, not claimed.
- [x] **M00.6 — Member 1 (solo scope):** Create `docs/acceptance-checklist.md` listing each final synopsis outcome and the evidence needed; create a concise PR checklist for page/documentation work. **Done when:** notifications and audit/history are included alongside scan and gate behavior. **Current completion evidence:** Completed by Member 1 under solo scope: docs/acceptance-checklist.md maps every final outcome to required sanitized evidence, including notifications and audit/history. docs/page-review-checklist.md covers documentation/UI review without configuring GitHub.

## M01 — Contracts, schema and foundation cleanup

Depends on M00.1 and M00.4. Member 2 can document each finalized contract as it becomes available.

- [x] **M01.1 — Member 1:** Finalize the proposed entities, relationships, ownership model, scan/incident states, UTC timestamp convention, severity policy, and endpoint inventory. Put the agreed contract in `docs/api-contract.md` with synthetic JSON examples. **Done when:** IDs, pagination/filter shapes, error format, and frontend DTOs are unambiguous. **Current completion evidence:** Finalized docs/api-contract.md: entity/FK invariants, ownership, states/transitions, UTC, severity/fingerprints, route inventory, ID compatibility, pagination/filter rules, synthetic JSON and TypeScript DTOs. Future APIs/tables are explicitly design only.
- [x] **M01.2 — Member 1:** Add versioned database migrations; baseline the existing `users` schema safely and document a separate procedure for fresh and already-used local databases. Replace uncontrolled schema updates with migration-backed validation. **Done when:** both paths work without losing existing users. **Current completion evidence:** Implemented Boot-managed Flyway, V1/V2, explicit guarded baseline version 0 and migration-backed Hibernate validation. Five real PostgreSQL migration tests passed for fresh/repeat/adoption/backfill/refusal paths. Actual local database backed up before successful adoption/start; no users deleted. See docs/database-migrations.md.
- [x] **M01.3 — Member 1:** Standardize error responses and map malformed/invalid requests to 400, authentication to 401, forbidden access to 403, missing resources to 404, duplicates to 409, and unexpected failures to a safe 500. Include security-filter entry-point/access-denied responses in the contract. **Done when:** clients receive consistent JSON without internal details. **Verified 11 October 2026:** implemented shared `ApiError`, MVC advice and JSON security handlers; registration prechecks and wrapped PostgreSQL unique violations return 409. [Error contract and verification](docs/api-contract.md) cover all required statuses and safe fixed messages. Wrapper compile and all 23 focused MVC/security/service tests passed without a database, using Java 26.0.2 with the Java 21 release target; `git diff --check` passed. This earlier check preceded integration setup; the final Java 21 unit and full PostgreSQL suites now pass (see M01.5).
- [x] **M01.4 — Member 1:** Add automatic creation/update timestamps where appropriate, explicit `open-in-view` policy, separate local/test configuration, and a minimal public liveness endpoint with no database or secret details. **Done when:** protected routes remain protected and configuration choices are verified. **Verified 11 October 2026:** added UTC JPA creation/update callbacks for `User`, removed manual registration timestamps, and explicitly disabled Open EntityManager in View. Added shared default-local configuration and test-only configuration requiring dedicated `TEST_DB_*` values; the original context test now selects `test`. Public `GET /api/health/live` returns only `{"status":"UP"}` and skips JWT/database lookup; `/api/health` and other protected routes remain protected. All 36 focused timestamp/configuration/MVC/security/service tests and `git diff --check` passed. [Configuration choices and verification limits](docs/runtime-configuration.md) document the current required update timestamp and migration-backed configuration; no development database was modified.
- [x] **M01.5 — Member 1:** Set up isolated PostgreSQL-backed test execution and reusable API test support, using a disposable test database or containers supported by both machines. **Done when:** tests do not modify the development database and required environment prerequisites are documented. **Verified 11 October 2026:** added Windows disposable-cluster runner, fail-closed connection/marker guard, transactional `PostgresApiTest` MockMvc/JSON support, and opt-in `postgres-tests` Maven profile. Renamed the context test to `SecretShieldApplicationIT` and added real PostgreSQL foundation integration tests. Initially 47 tests passed on PostgreSQL 18.6; the final migration-backed run passed 52 on Java 21; default database-independent suite passed 40 tests. Direct integration execution without the runner was rejected before database setup. Temporary server/data cleanup, script parsing, and `git diff --check` passed; development credentials/database were not used. [Windows prerequisites, commands, isolation and cleanup](docs/testing.md) include PostgreSQL server-tool installation for Member 2, whose second-machine run remains unverified. M01.2 replaced temporary create-drop with guarded Flyway migrations and Hibernate validation.
- [x] **M01.6 — Member 1 (taking the originally assigned Member 2 task):** Turn finalized design decisions into `docs/data-model.md` and an ER diagram; clarify existing versus planned entities. **Done when:** Member 1 checks the diagram against migrations/contracts. **Current completion evidence:** Finalized docs/data-model.md and current/future ER diagrams; checked all implemented User fields against entity, V1/V2 and PostgreSQL tests, and future relationships against M01.1. Only users is implemented; future resource migrations remain their later modules.

## M02 — Finish authentication

Depends on M01. Current registration/login/JWT code should be completed and verified, not rewritten without a reason.

- [x] **M02.1 — Member 1:** Add `GET /api/auth/me` as a useful permanent protected endpoint returning only safe user fields. Define explicit unauthorized behavior in the security filter chain. **Done when:** valid JWT gives 200; missing, tampered, expired, or unknown-user JWT gives JSON 401. **Verified 11 October 2026:** Implemented safe UserSummary with decimal-string id; /me is protected by the existing JWT chain. Real PostgreSQL tests verify 200 for valid tokens and fixed JSON 401 for missing, malformed, tampered, expired, removed/unknown-user tokens on /me and protected health.
- [x] **M02.2 — Member 1:** Normalize email consistently, define username/password bounds, add explicit duplicate-user exceptions, and handle database unique-constraint races. **Done when:** valid registration works, duplicates give 409, validation gives 400, and password hashes never appear in responses. **Verified 11 October 2026:** Normalized request/service/token email lookup; V3 canonicalizes stored email and adds normalized uniqueness/check constraints with collision refusal. Username ASCII 3-50 and password 8-72 UTF-16 units/max72 UTF-8 bytes validated before BCrypt. DuplicateUserException catches flushed PostgreSQL uniqueness races. Parallel real API requests returned one 201 and one 409; hashes and safe DTOs verified.
- [x] **M02.3 — Member 1:** Inspect the generated development-password/in-memory-user configuration reported in the progress PDF and remove unintended authentication paths. Verify JWT secret configuration fails clearly if absent/invalid. **Done when:** intended JWT authentication is the documented path and startup errors disclose no secrets. **Verified 11 October 2026:** Excluded servlet UserDetailsServiceAutoConfiguration (the source of the default generated password/user), disabled Basic/form/session logout, and kept stateless bearer authentication. Context tests prove no UserDetailsService exists and Basic cannot authenticate. Missing/short/unresolved signing keys fail with fixed secret-free messages; valid key and expiration checks passed.
- [x] **M02.4 — Member 1:** Decide the frontend session/token strategy, logout behavior, expiration UX, and allowed local frontend origin; configure CORS accordingly and revisit CSRF if using cookies. **Done when:** authenticated browser requests and preflight work, unsupported origins are rejected, and token handling is documented. **Verified 11 October 2026:** Decided memory-only JWT, explicit bearer header, client logout and 401 expiry UX in docs/auth-session.md; no cookies/refresh/session persistence. Added exact configurable FRONTEND_ORIGIN default localhost:5173, preflight-before-auth and safe JSON CORS 403. Real browser-shaped requests/preflight passed; untrusted origin/method/header denied. CSRF policy documented for bearer-only authentication; React implementation remains M11.
- [x] **M02.5 — Member 1:** Add meaningful tests for registration validation/conflicts, BCrypt storage, correct/wrong/unknown-user login, token expiry/tampering, `/me`, and protected routes. **Done when:** tests pass against isolated configuration and authentication is marked verified. **Verified 11 October 2026:** On Java 21/PostgreSQL 18.6: 45 database-independent tests and all 70 full tests passed with no failures/skips. Includes 9 authentication integration tests, actual concurrent registration, V3 normalization/collision preservation, JWT signing/config tests and a real HTTP test. Disposable server/data cleaned up; no development DB used.
- [x] **M02.6 — Member 1 (solo scope):** Prepare `docs/auth-manual-checks.md` with placeholder requests and expected statuses, based on M02.5; execute permitted checks with synthetic users. **Done when:** results are recorded with tokens/passwords removed. **Verified 11 October 2026:** Completed by Member 1 under solo scope: docs/auth-manual-checks.md contains private placeholder requests/statuses and scripts/check-auth.ps1. All 14 real HTTP checks passed on a random-port app with disposable PostgreSQL; synthetic account removed by test cleanup, tokens/passwords never printed. Manual local script explicitly requires -CreateSyntheticUser and documents that its account remains.

## M03 — Roles and authorization

Depends on M02.

- [x] **M03.1 — Member 1:** Replace free-form role Strings with controlled `DEVELOPER`/`ADMIN` values and migrate existing users. Add a controlled administrator bootstrap procedure. **Done when:** public registration cannot assign admin permissions. **Verified 12 October 2026:** added `UserRole`, enum JPA mapping and V4 constraint/normalization with refusal for unknown historical roles. Public registration remains forced to DEVELOPER even when JSON submits ADMIN. Added explicit, disabled-by-default, existing-user-only `scripts/bootstrap-admin.ps1`; it restores temporary settings and exposes no public promotion API.
- [x] **M03.2 — Member 1:** Define an access matrix: developers manage their own repository resources; admins have explicit cross-repository management rights. Implement reusable authorization helpers and protected admin endpoints only where needed. **Done when:** service/controller checks match the matrix. **Verified 12 October 2026:** `ResourceAuthorization` centralizes admin, owner-or-admin and nested repository checks for M04+ services. `/api/admin/**` requires ADMIN, and the narrow `GET /api/admin/users/{id}` endpoint repeats service authorization and returns only `UserSummary`. The finalized matrix is in `docs/user-roles.md`.
- [x] **M03.3 — Member 1:** Add negative authorization tests, including nested-resource ownership and a developer attempting admin actions. Extend this suite when each resource module arrives. **Done when:** cross-user reads/writes are rejected consistently. **Verified 12 October 2026:** Java 21 suites passed 54 database-independent and 86 full PostgreSQL tests with zero failures/skips. Coverage includes developer 403, admin cross-user 200, anonymous 401, unknown admin lookup 404, cross-owner denial, nested-parent mismatch, database role constraint, bootstrap behavior, and migration normalization/refusal. Disposable PostgreSQL cleanup passed; M04+ must extend ownership tests per resource.
- [x] **M03.4 — Member 1 (solo scope):** Document the approved access matrix in `docs/user-roles.md` and prepare developer/admin dashboard labels. **Done when:** labels and examples match Member 1's contract. **Verified 12 October 2026:** documented exact 401/403/404 ordering, current and future permissions, bootstrap commands, safe admin response, JWT/database authority behavior, and Developer/Administrator dashboard labels. Future UI implementation remains M11.

## M04 — Repository management

Depends on M03. Initial connection means storing validated GitHub metadata; OAuth and automated cloning are outside this first version.

- [x] **M04.1 — Member 1:** Add `ProjectRepository` entity, migration, repository interface, DTOs, and canonical GitHub owner/name/URL validation. Define duplicate identity per owner. **Done when:** valid repositories persist and invalid/duplicate submissions have documented responses. **Verified 12 October 2026:** added V5, the JPA entity/repository, compact page and repository DTOs, normalized GitHub metadata validation, derived canonical URLs, and owner-scoped database uniqueness. Invalid metadata returns 400 and normalized duplicates return 409.
- [x] **M04.2 — Member 1:** Implement create, paginated list, detail, metadata update, and deactivate operations with ownership checks. Preserve scan/history records rather than cascading away evidence. **Done when:** each user sees only authorized repositories and inactive repositories cannot start scans. **Verified 12 October 2026:** implemented all five protected routes, developer-owned/admin-all listing, explicit paging/filter/sort validation, immutable ownership/identity, default-branch-only updates, idempotent deactivation, restrictive owner FK, and an active authorized repository guard for M05 scan creation.
- [x] **M04.3 — Member 1:** Test ownership, URL validation, duplicates, pagination, and deactivation. **Done when:** two-user isolation and retained history pass. **Verified 12 October 2026:** 57 database-independent tests and 99 full disposable PostgreSQL tests passed with zero failures/skips. Ten repository integration tests cover canonical creation, invalid input, duplicates, database constraints, pagination/search/filtering, two-user 403 isolation, admin management, lifecycle retention, ID/query errors, and authentication. The disposable cluster stopped and was removed.
- [x] **M04.4 — Member 2:** Document repository terminology and prepare synthetic repository fixtures/page copy using M04 DTOs. **Done when:** fixtures contain no personal access tokens and cover active/inactive/empty states. **Completed by Member 1 under the current solo workflow, 12 October 2026:** `docs/repository-management.md` defines terminology, validation, endpoint behavior, access, and page copy; `docs/fixtures/repositories.json` supplies active/inactive/empty synthetic DTO fixtures with no credentials.

## M05 — Scan management and authenticated ingestion

Depends on M04. Finding persistence is added in M07; scan metadata and submission contracts come first.

- [ ] **M05.1 — Member 1:** Add Scan schema and DTOs with branch, commit SHA, trigger, run ID/attempt, scanner version, timestamps, and controlled status transitions. **Done when:** illegal transitions are rejected and failure is distinct from findings.
- [ ] **M05.2 — Member 1:** Implement authorized scan create/list/detail APIs with repository/status/date filters and pagination. Start with explicit local/manual scan records rather than arbitrary remote execution. **Done when:** the scan always belongs to the requested authorized repository.
- [ ] **M05.3 — Member 1:** Define the sanitized result schema and implement a separate repository-scoped machine-authentication mechanism for CI ingestion; store credential hashes or approved secure equivalents, with rotation/revocation. Bound payload size and validate commit/run/repository identity. **Done when:** invalid/revoked credentials or mismatched identities are rejected.
- [ ] **M05.4 — Member 1:** Make ingestion idempotent using documented repository/run/attempt/commit identity; transactionally complete scan metadata and provide a finding-persistence extension point. Reject conflicting replay payloads. **Done when:** network retries do not create duplicate scans and invalid submissions do not leave partial state.
- [ ] **M05.5 — Member 1:** Test lifecycle, ownership, machine scope, replay/conflicts, payload validation, and pagination. **Done when:** verified scan APIs and sanitized examples can be handed to frontend work.

## M06 — Local Gitleaks integration

Depends on M05. First scan controlled local test repositories; do not expose unrestricted filesystem paths or shell commands through a public endpoint.

- [ ] **M06.1 — Member 1:** Select and pin a Gitleaks version, verify its actual CLI flags/exit codes/report schema, and document installation. Create isolated clean and synthetic-secret fixture repositories outside normal tracked history. **Done when:** manual scans reproducibly distinguish clean, findings, and tool failures.
- [ ] **M06.2 — Member 1:** Implement a scanner adapter using an argument array, allowlisted workspace, timeout, bounded output, and temporary-file cleanup. Record tool/version/commit and explicit failures. **Done when:** no user input becomes a shell command and timeout/missing-tool cases are controlled.
- [ ] **M06.3 — Member 1:** Parse Gitleaks reports and immediately transform them to the sanitized M05 contract; drop raw secret/match/snippet fields before persistence, logs, HTTP, or artifacts. **Done when:** synthetic secret values cannot be found in the normalized output or application logs.
- [ ] **M06.4 — Member 1:** Connect the local adapter to Scan lifecycle and controlled execution; prevent duplicate execution or report mixing between simultaneous scans. **Done when:** clean, finding-bearing, and failed runs reach the right final states.
- [ ] **M06.5 — Member 1:** Test process/report errors, malformed/oversized results, timeouts, normalization, and concurrency boundaries. **Done when:** adapter tests plus one real pinned-scanner fixture run pass.
- [ ] **M06.6 — Member 2:** Write `docs/scanning-guide.md` from verified commands and record sanitized clean/finding examples. **Done when:** examples explain limitations and never instruct users to commit real credentials.

## M07 — Findings and severity

Depends on M06.

- [ ] **M07.1 — Member 1:** Add Finding schema, safe DTOs, stable identity/fingerprint rules, and rule-to-severity mapping with a documented fallback for unknown rules. Do not claim Gitleaks supplies the project's severity policy. **Done when:** every finding has safe location/rule metadata and deterministic severity.
- [ ] **M07.2 — Member 1:** Persist normalized findings transactionally with final scan results; update M05 ingestion to share the same normalization/persistence rules. Deduplicate retries while retaining occurrences in later scans. **Done when:** imports are repeatable and counts match stored findings.
- [ ] **M07.3 — Member 1:** Add authorized paginated list/detail endpoints and repository/scan/severity/rule filters. **Done when:** DTOs contain no raw secrets and nested ownership checks pass.
- [ ] **M07.4 — Member 1:** Test severity fallback, deduplication, safe output, repeated-scan occurrences, and owner isolation. **Done when:** malformed or sensitive input cannot leak into stored/displayed fields.
- [ ] **M07.5 — Member 2:** Prepare severity badge text, findings-table labels, and synthetic fixtures from the finalized DTOs. **Done when:** all severities and empty/error states have consistent presentation.

## M08 — Incident management, audit and history

Depends on M07.

- [ ] **M08.1 — Member 1:** Add Incident model and stable repository/finding linkage. Create one Open incident for a new identity and link later occurrences; define reopening when a remediated finding recurs. **Done when:** repeated imports do not create duplicate incidents.
- [ ] **M08.2 — Member 1:** Implement authorized Open/Resolved/Ignored transitions with reason, actor, timestamp, and concurrency protection. Define who may ignore and what evidence resolution requires. **Done when:** invalid transitions fail and recorded review status never changes a scanner result.
- [ ] **M08.3 — Member 1:** Add append-only audit events for repository changes, scans, incident transitions, and relevant admin actions. Use transactions for state changes plus audit writes; distinguish user/system/machine actors. **Done when:** committed actions have attributable history without secret values.
- [ ] **M08.4 — Member 1:** Add authorized paginated incident and audit-history APIs with repository/status/date filters. **Done when:** users cannot retrieve another owner's history or modify audit records through an API.
- [ ] **M08.5 — Member 1:** Test incident creation/reopening, transitions, concurrent changes, audit completeness, retry behavior, and ownership. **Done when:** the lifecycle and history match the contract.
- [ ] **M08.6 — Member 2:** Write incident workflow help text and manual scenarios for resolution, ignoring, recurrence, and history. **Done when:** documentation explains that leaked credentials require external rotation/revocation and that ignoring does not unblock deployment.

## M09 — Security gate and deployment prevention

Depends on M06–M08. The GitHub gate is enforced by M12; the backend records and explains the same policy.

- [ ] **M09.1 — Member 1:** Define a policy: CLEAN with successful validated scanning allows; findings, FAILED, incomplete, missing, or malformed results block. Bind decisions to repository and commit SHA. **Done when:** the policy table covers all scan states.
- [ ] **M09.2 — Member 1:** Implement a gate decision service and safe decision DTO with reason and scan identity. Separate incident review state from gate logic. **Done when:** resolving/ignoring incidents cannot convert an unsafe scan into ALLOW.
- [ ] **M09.3 — Member 1:** Test clean/unsafe/failure decisions and wrong/stale-commit results. Define reporting-outage behavior without bypassing the scanner gate. **Done when:** every error path preserves deployment blocking where scan safety is unknown.
- [ ] **M09.4 — Member 2:** Prepare dashboard gate status messages and the demo policy table. **Done when:** blocked-by-findings and blocked-by-scan-failure are visibly distinct.

## M10 — High-risk notifications with Azure Communication Services

Depends on M08. Implement locally with a fake provider first; live Azure validation depends on M14.

- [ ] **M10.1 — Member 1:** Define high-risk severity threshold, authorized recipient configuration, event identity, payload, and deduplication policy. Add notification-delivery records. **Done when:** retries/repeated scan imports cannot create uncontrolled duplicate alerts.
- [ ] **M10.2 — Member 1:** Implement provider interface plus local fake provider and queued/outbox delivery after transaction commit. Track retryable failures with bounded retry behavior. **Done when:** notification failure does not roll back findings or alter the gate.
- [ ] **M10.3 — Member 1:** Implement the ACS provider with externalized credentials and sanitized alert content: repository, severity, incident link, and remediation guidance. Include safe delivery status in APIs. **Done when:** tests prove no secret values/tokens enter messages or logs.
- [ ] **M10.4 — Member 1:** After M14, configure an approved test recipient and the required ACS sender/domain/channel setup; verify a real high-risk notification plus a failure/retry case. **Done when:** sanitized evidence identifies actual success separately from fake-provider success.
- [ ] **M10.5 — Member 2:** Write notification help text and capture reviewed delivery evidence for the report. **Done when:** documented behavior matches the configured threshold and retry policy.

## M11 — Dashboard APIs and React frontend

Member 1 owns shared setup, contracts, API integration, session handling, and mutations. Member 2 owns the listed presentation files. M11.1 can begin after M01.1; fixture-driven pages can proceed alongside backend work. Live integration waits for the relevant APIs.

- [ ] **M11.1 — Member 1:** Create `frontend/`, choose/pin its tooling, add shared layout/router/theme, and publish stable typed props/DTOs and synthetic fixtures in agreed files. Establish filenames for Member 2's pages and components. **Done when:** development/build commands work and fixtures cover loading, empty, success, and error states.
- [ ] **M11.2 — Member 1:** Implement authorized dashboard summary APIs: scan counts/outcomes, finding severities, incident counts, recent activity, and repository security summaries. **Done when:** aggregation tests verify counts, filters, owner isolation, and no misleading clean status without a valid scan.
- [ ] **M11.3 — Member 1:** Implement API client, session handling, login/register functional forms, protected routes, expiration handling, and shared request/error behavior according to M02.4. **Done when:** browser registration/login/logout and unauthorized redirects work against the backend.
- [ ] **M11.4 — Member 2:** Build the overview page and reusable summary cards/severity/status badges using supplied props and fixtures. **Done when:** all states render, responsive layout works, and labels match backend terminology.
- [ ] **M11.5 — Member 2:** Build repository and scan list/detail presentation with supplied filter/page callbacks. **Done when:** branch/commit/time/outcome are clear, tables have empty/loading/error views, and no backend mutation logic is embedded in page components.
- [ ] **M11.6 — Member 2:** Build findings and incident list/detail presentation plus a history timeline. Render supplied transition controls/callbacks and permission flags. **Done when:** severity, safe location, reason, and actor/time are readable and inaccessible actions are not offered.
- [ ] **M11.7 — Member 2:** Add keyboard-accessible controls, field labels, readable contrast, consistent date formatting, help text, and mobile/table overflow behavior across assigned pages. **Done when:** the page review checklist passes with synthetic fixtures.
- [ ] **M11.8 — Member 1:** Connect presentation components to live APIs, pagination/filters, repository actions, and incident mutations; handle conflicts, forbidden responses, and notification status. **Done when:** a full local scan-to-incident-review flow works in the browser without secrets in browser logs.
- [ ] **M11.9 — Member 1:** Add focused frontend integration checks for session expiry, ownership/error handling, and incident state updates. Run production build. **Done when:** critical user flows pass with finalized API contracts.
- [ ] **M11.10 — Member 2:** Run the manual UI checklist and capture sanitized screenshots; report bugs with steps, expected behavior, and actual behavior. **Done when:** Member 1 resolves functional defects and the verified UI evidence is ready for the report.

## M12 — GitHub Actions and automated scanning

Depends on M09 and the M05/M07 authenticated ingestion contract. Image-build steps require M13. Never make deployment depend on an unchecked dashboard status alone.

- [ ] **M12.1 — Member 1:** Add a workflow for relevant pushes and PRs with minimal permissions, pinned scanner/actions, required source history, fixed scan scope, and distinct clean/finding/tool-error outcomes. **Done when:** actual GitHub runs demonstrate clean, synthetic finding, and scanner failure behavior.
- [ ] **M12.2 — Member 1:** Make build/release/deployment jobs depend on successful clean scanning of the same commit. Keep reporting separate so reporting on a failed scan cannot make deployment eligible. **Done when:** secret/error runs visibly skip deployment and no unconditional continuation bypasses the gate.
- [ ] **M12.3 — Member 1:** Submit only sanitized reports through the machine-authenticated backend API, with bounded retries and run/attempt identity. Run reporting after finding-bearing scans where credentials are available. **Done when:** GitHub findings appear once in the dashboard and raw reports are never uploaded as ordinary artifacts.
- [ ] **M12.4 — Member 1:** Define trusted versus fork PR behavior: untrusted code receives no backend/cloud secrets, but scanner gating still runs. Prevent workflows from executing privileged untrusted PR code. **Done when:** fork/missing-credential behavior is tested and documented rather than reported as successful ingestion.
- [ ] **M12.5 — Member 1:** Add backend tests, frontend checks, and image build on the allowed path; reserve Azure deployment for the agreed trusted branch/environment and deployment identity. **Done when:** the required PR checks cover both members' contributions and PRs cannot deploy with production credentials.
- [ ] **M12.6 — Member 2:** Document workflow stages and capture sanitized GitHub evidence for clean, blocked, and scanner-failure runs. **Done when:** screenshots include commit/run identity and match stored scan records.

## M13 — Docker and local full-stack execution

Depends on runnable backend and frontend from M11; can proceed before final cloud integration.

- [ ] **M13.1 — Member 1:** Add backend multi-stage Docker build and `.dockerignore`; choose the frontend serving arrangement compatible with the Azure App Service plan. **Done when:** images build reproducibly and no environment secret or raw report is copied into them.
- [ ] **M13.2 — Member 1:** Add local Compose services for PostgreSQL/backend/frontend, safe environment placeholders, persistent DB volume, and startup/readiness behavior. Include Gitleaks only where the chosen local scanning path needs it. **Done when:** migrations run and the browser can reach authenticated APIs in containers.
- [ ] **M13.3 — Member 1:** Verify restart/persistence, image startup, clean/finding scan flows, and environment handling. Define a non-destructive shutdown command and a separately labeled data-reset procedure. **Done when:** a fresh clone can run the full stack from documented commands.
- [ ] **M13.4 — Member 2:** Update local setup docs with the verified Docker path and troubleshooting. **Done when:** teammate reproduction succeeds and no commands silently delete database volumes.

## M14 — Terraform and Azure infrastructure

Depends on M13 and verified local flow. Azure resource creation requires the team's subscription, region, budget, and credentials; prepare the Terraform and review its plan before applying it. Resource names/SKUs are finalized at implementation time.

- [ ] **M14.1 — Member 1:** Define hosting arrangement, App Service container/image source, managed PostgreSQL networking, ACS sender requirements, region, naming, budget, secret storage, and identities. Record provider-specific choices in `docs/cloud-design.md`. **Done when:** architecture fits the synopsis and expected costs/required account inputs are explicit.
- [ ] **M14.2 — Member 1:** Create `infra/` Terraform for required resource group, app hosting, managed PostgreSQL, image registry if needed, and ACS resources supported by the chosen configuration. Pin providers, use variable examples, and keep state/plans out of Git. **Done when:** formatting and validation pass and credentials are not hardcoded.
- [ ] **M14.3 — Member 1:** Configure durable protected Terraform state, deployment identity, least-privilege access, database TLS/network controls, and externalized app secrets; document any manual ACS verification steps. **Done when:** state access and secret provisioning are documented and app/database exposure matches the design.
- [ ] **M14.4 — Member 1:** Generate and inspect the Terraform plan with supplied account inputs, then apply within authorized subscription/budget scope. Record outputs without secrets. **Done when:** intended resources exist and no unrelated resources were changed.
- [ ] **M14.5 — Member 2:** Draw the final Azure architecture diagram using actual reviewed resources and update the resource inventory. **Done when:** diagram matches Terraform and includes notifications and managed database.

## M15 — Azure deployment and operations

Depends on M12–M14; complete live notification validation M10.4 here if pending.

- [ ] **M15.1 — Member 1:** Connect trusted GitHub deployment to the selected image registry/App Service path with appropriate deployment identity and environment settings. **Done when:** clean main-branch commits deploy the scanned image/commit and failed gates cannot deploy.
- [ ] **M15.2 — Member 1:** Configure PostgreSQL connectivity/migrations, frontend API origin, HTTPS, CORS/session behavior, liveness/readiness, and ACS settings. **Done when:** cloud login and authorized dashboard APIs work without mixed-origin/security failures.
- [ ] **M15.3 — Member 1:** Run the cloud acceptance flow: clean deployment, synthetic-secret blocked deployment, scan ingestion, incident transition, audit record, and real high-risk notification. **Done when:** evidence links one workflow commit/run through dashboard and delivery records.
- [ ] **M15.4 — Member 1:** Add safe operational logging, failed-scan/reporting/notification visibility, database backup/recovery approach, application rollback instructions, and controlled teardown steps. **Done when:** logs contain no secrets and rollback/recovery procedures are reviewed for the actual hosting model.
- [ ] **M15.5 — Member 2:** Write `docs/deployment-guide.md` and collect reviewed cloud screenshots using M15 evidence. **Done when:** docs clearly separate automated Terraform resources from manual prerequisites.

## M16 — Final testing, documentation and demonstration

Depends on all required modules. Notifications and audit/history are required outcomes, not optional polish.

- [ ] **M16.1 — Member 1:** Run final automated checks and end-to-end tests across authentication, two-user isolation, repositories, scans, scanner failures, findings, incident lifecycle, audit, retries, notifications, and gate enforcement. **Done when:** failures are fixed or accurately recorded as remaining limitations.
- [ ] **M16.2 — Member 1:** Review tracked files, logs, images, reports, browser output, Terraform artifacts, and demo fixtures for exposed values; verify machine credential revocation and admin restrictions. **Done when:** reviewed deliverables contain no real secrets and critical negative tests pass.
- [ ] **M16.3 — Member 2:** Complete the acceptance checklist and prepare the technical report: problem, scope, architecture, module descriptions, cloud/DevOps decisions, actual results, limitations, and future work. **Done when:** every claimed outcome links to real evidence and Member 1 reviews technical accuracy.
- [ ] **M16.4 — Member 2:** Prepare slides and `docs/demo-script.md`: login, clean run, synthetic leak, blocked deployment, finding review, incident update, history, high-risk notification, and Azure/Terraform evidence. **Done when:** screenshots and fallback recordings use synthetic/sanitized data.
- [ ] **M16.5 — Member 1:** Review the final docs/slides and rehearse the complete demo with Member 2. Check clean-clone setup, deployment commit identity, and safe recovery if a cloud service is unavailable. **Done when:** the team can explain each module and reproduce the demonstration.
- [ ] **M16.6 — Member 1:** Merge reviewed PRs, tag the tested final version, and document resource retention/teardown decisions. **Done when:** the final branch is reproducible, checks pass, and cloud cleanup occurs only when explicitly authorized.

## Parallel work lanes and merge rules

| Stage | Member 1 | Member 2 |
| --- | --- | --- |
| Foundation | M00 setup, M01 contracts/migrations, M02 authentication | Setup guide, acceptance checklist, ER diagram after contracts |
| Backend | M03–M10; provide M11.1 shell/contracts early | Repository/severity/help fixtures, M11.4–M11.7 page presentation |
| Integration | M11 live API wiring, M12 workflows, M13 Docker | UI acceptance, screenshots, scanning/workflow/setup guides |
| Cloud | M14 Terraform, M15 deployment, live ACS verification | Actual Azure diagram, deployment documentation, demo evidence |
| Delivery | M16 technical verification, review, final integration | M16 report, presentation, demo script |

- Member 1 owns `pom.xml`, backend `src/`, application configuration, `.github/`, Docker/Compose, `infra/`, frontend dependency manifests/lockfiles, shared router/API/session code, and automated security tests.
- Member 2 owns assigned frontend presentation files, diagrams, and assigned `docs/`/report/slide files. Decide exact frontend filenames in M11.1 before parallel page work.
- Member 1 supplies fixtures and callbacks; Member 2 does not need to modify authentication, backend permissions, gate logic, or cloud configuration to build pages.
- Pull the latest default branch before starting a step. Create a feature branch; keep changes to the step's files; open a PR with acceptance evidence; merge after review and required checks.
- A contract change belongs in a Member 1 PR with updated examples/fixtures and a note to Member 2 before page integration.
- This todo is maintained by Member 1 to avoid competing status edits. Member 2 lists completed step IDs and evidence in each PR; Member 1 updates the shared checklist after review.
- Safe parallel work uses separate files. If two tasks need the same shared file, finish and merge the prerequisite first.

## Start here

Modules M00 (except deferred GitHub collaboration), M01, M02, M03 and M04 are complete
under the solo scope. The next implementation module is M05 scan management and result ingestion.

Foundation completion record (11 October 2026): All M00/M01 steps in the authorized solo scope are complete. M00.2 and M00.3 remain explicitly deferred; no GitHub settings or second-machine success are claimed. Later modules remain unchecked.

Module 02 completion record: M02.1-M02.6 implemented and verified by Member 1, including the original Member 2 documentation task. Its completion suites had 45 unit tests and 70 full tests.

Module 03 completion record (12 October 2026): M03.1-M03.4 implemented by Member 1 under the solo scope. That checkpoint passed 54 database-independent and 86 full tests. V4 also migrated the configured Neon database successfully; live smoke checks passed and the backend was stopped.

Module 04 completion record (12 October 2026): M04.1-M04.4 implemented by Member 1 under the current solo workflow. Current suites pass 57 database-independent and 99 full disposable PostgreSQL tests. V5 and repository API behavior are verified in isolation; the configured development database will migrate on its next normal startup. M05 and later implementation remain unchecked.
