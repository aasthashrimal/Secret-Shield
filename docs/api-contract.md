# Secret Shield API contract

Foundation contract finalized by Member 1 on 11 October 2026 (M01.1).
Authentication, health, roles, errors, repository, scan, finding, incident, and audit management are implemented.
Dashboard and notification contracts below remain
implementation targets for later modules; documenting an endpoint does not make it available.

## Error responses

Controller errors and security-filter errors use `Content-Type: application/json`
and the same four-field body. The HTTP status matches the `status` field.

```json
{
  "success": false,
  "status": 400,
  "code": "BAD_REQUEST",
  "message": "Invalid request"
}
```

| HTTP status | Code | Message | Trigger |
| --- | --- | --- | --- |
| 400 | `BAD_REQUEST` | Invalid request | Invalid DTO fields, malformed/missing JSON, invalid request parameters, or constraint validation |
| 401 | `UNAUTHORIZED` | Authentication required or credentials invalid | Invalid login credentials; missing/invalid/unknown-user bearer credentials on protected routes; JWT parsing failures |
| 403 | `FORBIDDEN` | Access denied | Authenticated request rejected by authorization, including security-filter access denial |
| 404 | `NOT_FOUND` | Resource not found | Missing authenticated route/static resource, or `ResourceNotFoundException` |
| 409 | `CONFLICT` | Resource already exists or conflicts with current state | Duplicate registration, `ConflictException`, `DuplicateKeyException`, or a wrapped PostgreSQL unique violation (`23505`) |
| 500 | `INTERNAL_SERVER_ERROR` | An unexpected error occurred | Unexpected application failure or a database constraint/infrastructure failure that is not a duplicate |

Other framework errors retain their HTTP status and protocol headers: for example,
405 uses `METHOD_NOT_ALLOWED` / `Method Not Allowed` and retains `Allow`; 415 uses
`UNSUPPORTED_MEDIA_TYPE` / `Unsupported Media Type`. They use the same JSON shape.
Framework 5xx responses use the generic internal-error code and message.

Messages are fixed public text. Responses omit exception messages, stack traces,
SQL details, rejected input values, passwords, JWTs, and source snippets. Validation
responses deliberately omit field-specific details. Clients should use `status`
and `code` for decisions rather than parsing `message`.

Security authentication failures include `WWW-Authenticate: Bearer` and do not
redirect to a login page. Security runs before controller routing/validation:
an unauthenticated request to a protected or nonexistent route receives 401 first.
Registration and login remain public. M01.4 also makes the exact
`GET /api/health/live` route public; see [runtime configuration](runtime-configuration.md).
An authentication database outage returns
the safe 500 body rather than being reported as invalid credentials.

Successful registration/login responses retain their existing DTO shapes;
they are not wrapped in this error envelope. The older `ApiResponse` DTO is
not the error contract.

## Implementation and verification

`ApiError` owns the public body. `GlobalExceptionHandler` handles application and
Spring MVC exceptions. `JsonSecurityErrorHandler` implements the Spring Security
[authentication entry point](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/web/AuthenticationEntryPoint.html)
and [access-denied handler configuration](https://docs.spring.io/spring-security/reference/7.0/api/java/org/springframework/security/config/annotation/web/configurers/ExceptionHandlingConfigurer.html).
The JWT filter also uses this writer for authentication parsing/infrastructure errors.

Verified on 11 October 2026 with the checked-in wrapper and unchanged dependencies:

```powershell
.\mvnw.cmd -o -DskipTests compile
.\mvnw.cmd -o '-Dtest=ApiErrorHandlingTests,AuthServiceConflictTests' test
```

All 23 focused tests passed without a database or private environment values.
Coverage includes the six required statuses, malformed/missing JSON, DTO validation,
duplicate prechecks and wrapped database unique violations, MVC and filter-level
authorization failures, JWT parsing and authentication database failures, authenticated
missing routes, preserved framework statuses/headers, unchanged success responses,
and absence of synthetic sensitive exception/input text in error bodies.

The initial focused check used Java 26 with a Java 21 release target. The final foundation verification reran compilation and all 40 unit tests on Java 21, plus all 52 disposable PostgreSQL tests. See [testing](testing.md). Comprehensive JWT expiry/tampering remains later authentication work.

## Identifiers, time, serialization and pagination

Database primary keys are positive PostgreSQL `BIGINT` / Java `Long` values.
New resource DTOs serialize IDs as decimal **strings** to preserve all 64-bit values
in browsers. URL IDs must be positive decimal strings without signs or leading zeros.
Existing registration keeps its numeric `id` for compatibility; that exception has
its own frontend type below. No clients should serialize an entity directly.

Stored timestamps use PostgreSQL `TIMESTAMP(6) WITHOUT TIME ZONE` with UTC semantics
and Java `LocalDateTime`; DTOs explicitly serialize UTC with a `Z`, for example
`2026-10-11T09:00:00Z`. Nullable fields are emitted as JSON `null`, never omitted.
Enums use uppercase spellings. Creation times are immutable; mutable resources
have update times. Audit events and finding payloads are append-only; a finding may receive its incident linkage once. No hard deletes cascade
through repository history.

Every collection endpoint returns `Page<T>` with zero-based `page`, `size` (default
20, range 1–100), `totalElements`, `totalPages`, and `content`. Empty pages have
`content: []`; an empty collection has `totalPages: 0`. Negative pages, out-of-range
sizes, unknown filters/sort fields, malformed IDs/enums/timestamps return 400.
`sort=createdAt,desc` is the default, with `id,desc` as a deterministic tie-breaker.
Audit uses `occurredAt,desc`; sorting only allows `id` and the endpoint's timestamp
field. Unknown IDs give 404 to authenticated callers; known unauthorized resources
give 403. Authentication is checked first.

Date filters are `from` (inclusive) and `to` (exclusive), UTC ISO-8601 instants, on
creation time (audit: occurrence time); reject `from >= to`. Filters combine with AND.
Repository lists accept `active` and `q` (case-insensitive owner/name search, at most
100 characters). Scans accept `status`, `branch`, `commitSha`, `from`, `to`.
Findings accept `severity`, `ruleId`, `from`, `to`; incidents accept `status`,
`severity`, `from`, `to`. Dashboard filters accept `repositoryId`, `from`, `to`.
Branch filters are exact matches; commit SHA is a lowercase 40-character hex SHA-1
in this first version. Notification lists accept `status`; audit lists accept
`operation`, `actorType`, `from`, `to`. No comma-separated enum filters in version 1.

```json
{
  "content": [{"id":"101","ownerId":"1","githubOwner":"demo-team","githubName":"synthetic-repo","canonicalUrl":"https://github.com/demo-team/synthetic-repo","defaultBranch":"main","active":true,"createdAt":"2026-10-11T09:00:00Z","updatedAt":"2026-10-11T09:00:00Z"}],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## Entities, ownership and integrity

`User`, `ProjectRepository`, `Scan`, `CiCredential`, `Finding`, `Incident`, and `AuditEvent` are currently implemented. [data-model.md](data-model.md)
distinguishes the migration-backed current schema from future entities. Remaining
tables/fields below are finalized design targets for their own modules' migrations.

| Entity | Required fields / relationships | Uniqueness and lifecycle |
| --- | --- | --- |
| User | id, username, email, BCrypt password, role, createdAt, updatedAt | Unique username/email; timestamp backfill V2; email normalization V3; controlled `DEVELOPER`/`ADMIN` role and constraint V4 |
| ProjectRepository | id, ownerId → User, githubOwner, githubName, canonicalUrl, defaultBranch, active, createdAt, updatedAt | Unique `(ownerId, githubOwner, githubName)` after lowercasing GitHub identity; deactivate rather than delete |
| Scan | id, repositoryId → ProjectRepository, commitSha, branch, trigger, status, scannerVersion (nullable before execution), runId/runAttempt (nullable for manual scans), startedAt/completedAt (nullable before relevant transition), failureCode (nullable), createdAt, updatedAt | CI unique `(repositoryId, runId, runAttempt, commitSha)`; payload digest distinguishes identical vs conflicting replay |
| Finding | id, scanId → Scan, incidentId → Incident (nullable until incident linkage), fingerprint, ruleId, severity, filePath, line, commitSha, sanitizedDescription, createdAt | Unique `(scanId, fingerprint)`; immutable occurrence; incident must have the same repository as scan |
| Incident | id, repositoryId → ProjectRepository, fingerprint, severity (maximum observed), status, reviewReason (nullable while open), lastActorType/reference (nullable at creation), version, createdAt, updatedAt | Unique `(repositoryId, fingerprint)`; optimistic locking; one incident groups multiple scan occurrences using Finding.incidentId |
| CiCredential | id, repositoryId → ProjectRepository, secretHash, createdAt, expiresAt (nullable), revokedAt (nullable) | Separate repository-scoped random credential; salted BCrypt hash; secret returned only once at issuance, never in list/detail |
| AuditEvent | id, repositoryId → ProjectRepository, actorType, actorUserId → User (nullable), actorCredentialId → CiCredential (nullable), operation, targetType, targetId, safeChangeSummary, occurredAt | `USER` requires only user ID; `MACHINE` requires only credential ID in same repository; `SYSTEM` has neither; append-only and transactionally recorded with mutation |
| NotificationDelivery | id, incidentId → Incident, auditEventId → AuditEvent, recipientConfigReference, status, attemptCount, safeErrorCode (nullable), createdAt, updatedAt | Unique `(auditEventId, recipientConfigReference)`; event and incident must have the same repository; authorized recipients come from private server configuration |

Ownership is inherited from the repository. `DEVELOPER` can manage only owned
repositories and their nested resources. `ADMIN` has explicit cross-repository
management rights. Both may resolve incidents; only admins may ignore. Public
registration cannot request an admin role. Resource ownership cannot be changed
through metadata updates. Credential issuance/revocation belongs to an owner/admin;
machine credentials only submit results for their repository. They cannot use
interactive user APIs. No raw exposed secret, snippet, scanner report, bearer token,
provider payload, or plaintext machine credential is persisted in findings/history.

## State and severity policies

Scan triggers: `MANUAL`, `CI_PUSH`, `CI_PR`. Transitions:
`QUEUED → RUNNING | FAILED`; `RUNNING → CLEAN | FINDINGS_DETECTED | FAILED`.
Terminal states cannot be overwritten. Ingestion validates payload before changing
state; identical terminal replay returns the existing result, conflicting replay is
409. Tool failure, timeout, malformed/missing reports never become `CLEAN`.

Incidents start `OPEN`. `OPEN → RESOLVED | IGNORED`; `RESOLVED | IGNORED → OPEN`
via an authorized review action. A new occurrence after resolution automatically
reopens the incident and records a system audit event; ignored incidents stay ignored
on recurrence but still receive occurrences. Resolve/ignore require a nonblank
reason of 1–1000 characters and the current `version`; version mismatches return 409.
Review actions never change finding/scan outcomes or bypass the gate.

Severity order is `CRITICAL > HIGH > MEDIUM > LOW > INFO`. Initial deterministic
project mapping (Gitleaks does not supply this project's severity policy):

| Rule ID | Severity |
| --- | --- |
| `private-key`, `aws-access-token` | CRITICAL |
| `generic-api-key`, `github-pat`, `github-fine-grained-pat` | HIGH |
| `generic-password` | MEDIUM |
| `synthetic-low` | LOW |
| `synthetic-info` | INFO |
| Any unmapped rule | HIGH |

The synthetic rule IDs are test fixtures, not assertions that the scanner ships them.
M06 must check known rule IDs against the pinned scanner; changing this dictionary
requires a contract change and fixtures. High-risk notification threshold is HIGH
or CRITICAL. Every finding blocks deployment regardless of severity or review status.

Fingerprint version 1 is lowercase SHA-256 of UTF-8
`repositoryId + "\n" + ruleId + "\n" + normalizedRelativePath + "\n" + decimalLine`.
Paths use `/`, reject absolute paths/`..` components, and omit source content.
Commit and secret values are excluded so unchanged locations group across scans.
Moving a finding to another line creates a new identity in version 1; this limitation
is explicit rather than claiming secret-based deduplication.

Notification states: `QUEUED → SENDING → SENT | FAILED`; failed attempts may be
queued again up to three total attempts. Same event/recipient is never inserted twice.
Retries reuse the delivery record; only safe error codes are returned. A clean gate
requires the newest exact-repository/commit scan to be complete, validated, and clean;
no scan, pending scan, finding-bearing scan, failed scan, or malformed clean record
yields BLOCK. See [gate policy and dashboard messages](gate-policy.md).

## Endpoint inventory

Current authentication request/response DTOs remain unchanged. All entries marked planned target future modules; /me is implemented in M02. IDs in every path below denote the decimal string form.

| Method and path | Access | Success and body | State |
| --- | --- | --- | --- |
| POST `/api/auth/register` | Public | 201, existing RegisterResponse | Implemented |
| POST `/api/auth/login` | Public | 200, existing LoginResponse | Implemented |
| GET `/api/health/live` | Public | 200, `{"status":"UP"}` | Implemented |
| GET `/api/health` | User | 200, current plain-text health message | Implemented |
| GET `/api/auth/me` | User | 200, UserSummary | Implemented M02 |
| GET `/api/admin/users/{id}` | Admin | 200, UserSummary; safe role inspection | Implemented M03 |
| POST `/api/repositories` | User | 201, RepositoryDto | Implemented M04 |
| GET `/api/repositories` | User, ownership-filtered/admin | 200, Page<RepositoryDto> | Implemented M04 |
| GET `/api/repositories/{id}` | Owner/admin | 200, RepositoryDto | Implemented M04 |
| PATCH `/api/repositories/{id}` | Owner/admin | 200, RepositoryDto; only defaultBranch editable | Implemented M04 |
| POST `/api/repositories/{id}/deactivate` | Owner/admin | 204; idempotent, retains history | Implemented M04 |
| POST `/api/repositories/{id}/scans` | Owner/admin, active repo | 201, ScanDto; manual QUEUED metadata | Implemented M05 |
| GET `/api/repositories/{id}/scans` | Owner/admin | 200, Page<ScanDto> | Implemented M05 |
| GET `/api/repositories/{id}/scans/{scanId}` | Owner/admin | 200, ScanDto | Implemented M05 |
| POST `/api/repositories/{id}/scans/{scanId}/start` | Owner/admin | 202, terminal ScanDto; controlled synchronous local scanner | Implemented M06 |
| POST `/api/repositories/{id}/ci-credentials` | Owner/admin | 201, id plus one-time issued credential | Implemented M05 |
| POST `/api/repositories/{id}/ci-credentials/{credentialId}/revoke` | Owner/admin | 204; idempotent | Implemented M05 |
| POST `/api/ci/repositories/{id}/scan-results` | Repository machine credential only | 201 first import, 200 identical replay, ScanDto | Implemented M05; findings persist in M07 |
| GET `/api/repositories/{id}/scans/{scanId}/findings` | Owner/admin | 200, Page<FindingDto> | Implemented M07 |
| GET `/api/repositories/{id}/findings/{findingId}` | Owner/admin | 200, FindingDto | Implemented M07 |
| GET `/api/repositories/{id}/incidents` | Owner/admin | 200, Page<IncidentDto> | Implemented M08 |
| GET `/api/repositories/{id}/incidents/{incidentId}` | Owner/admin | 200, IncidentDto | Implemented M08 |
| PATCH `/api/repositories/{id}/incidents/{incidentId}` | Owner/admin; ignore admin-only | 200, IncidentDto; status/reason/version | Implemented M08 |
| GET `/api/repositories/{id}/audit-events` | Owner/admin | 200, Page<AuditEventDto> | Implemented M08 |
| GET `/api/repositories/{id}/gate?commitSha=...` | Owner/admin | 200, GateDto (ALLOW/BLOCK, not an HTTP failure) | Implemented M09 |
| GET `/api/repositories/{id}/incidents/{incidentId}/notifications` | Owner/admin | 200, Page<NotificationDto> | Implemented M10 |
| GET `/api/dashboard/overview` | User, ownership-filtered/admin | 200, DashboardOverviewDto | Implemented M11 |

Repository creation body: `githubOwner`, `githubName`, `defaultBranch` (default
`main`); the server derives canonical URL/ownerId/active. Manual scan body: `branch`
and `commitSha`; the server derives trigger/status/repositoryId. Mutation input must
not accept ownership, roles, server timestamps or raw findings. Nested IDs are
validated against the parent repository/scan, not merely loaded globally.

M04 trims and lowercases GitHub owner/name before persistence. Owner is 1–39 ASCII
letters/digits/hyphens, cannot begin/end with a hyphen, and rejects consecutive
hyphens. Repository name is 1–100 ASCII letters/digits plus `.`, `_`, or `-`, with
`.` and `..` rejected. The server derives exactly
`https://github.com/{owner}/{name}` and does not accept a URL, token, clone credential,
or ownership field. Identity is unique by application owner plus normalized GitHub
owner/name. `defaultBranch` is a conservative 1–255-character Git ref subset; invalid
metadata is 400 and duplicate identity is 409. PATCH accepts only `defaultBranch`.

CI result input contains `runId` (string), positive `runAttempt`, `commitSha`,
`branch`, `trigger` (`CI_PUSH|CI_PR`), `scannerVersion`, terminal `status`, safe
`failureCode` or `findings` (ruleId/filePath/line/sanitizedDescription only).
Maximum body 1 MiB and 1000 findings; oversized body 413. Reject secret/snippet/raw
report fields and conflicting identities. Fingerprints/severities are computed by
the server; callers cannot select a clean status alongside findings. The credential
format is `ssci_v1.<credentialId>.<secret>` in `X-Secret-Shield-Credential`.
Only its BCrypt hash is stored; the full value is returned once. Issue a replacement
then revoke the old ID to rotate. Interactive JWTs are rejected here.

## Frontend DTO shapes (implementation targets)

These types are the single contract for future API/presentation fixtures. A string
timestamp is a UTC ISO-8601 instant; `Id` is the decimal representation defined above.
No credential hash or user password field is present.

```typescript
type Id = string;
type Instant = string;
type Role = "DEVELOPER" | "ADMIN";
type Severity = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "INFO";
type ScanStatus = "QUEUED" | "RUNNING" | "CLEAN" | "FINDINGS_DETECTED" | "FAILED";
type IncidentStatus = "OPEN" | "RESOLVED" | "IGNORED";
type ActorType = "USER" | "MACHINE" | "SYSTEM";
interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
interface ExistingRegistrationResponse { id: number; username: string; email: string; role: string }
interface ExistingLoginResponse { token: string; username: string; email: string; role: string }
interface UserSummary { id: Id; username: string; email: string; role: Role }
interface RepositoryDto { id: Id; ownerId: Id; githubOwner: string; githubName: string; canonicalUrl: string; defaultBranch: string; active: boolean; createdAt: Instant; updatedAt: Instant }
interface ScanDto { id: Id; repositoryId: Id; commitSha: string; branch: string; trigger: "MANUAL" | "CI_PUSH" | "CI_PR"; runId: string | null; runAttempt: number | null; scannerVersion: string | null; status: ScanStatus; failureCode: string | null; startedAt: Instant | null; completedAt: Instant | null; createdAt: Instant; updatedAt: Instant }
interface FindingDto { id: Id; scanId: Id; incidentId: Id | null; fingerprint: string; ruleId: string; severity: Severity; filePath: string; line: number; commitSha: string; sanitizedDescription: string; createdAt: Instant }
interface IncidentDto { id: Id; repositoryId: Id; fingerprint: string; severity: Severity; status: IncidentStatus; reviewReason: string | null; lastActorType: ActorType | null; lastActorReference: Id | null; version: number; createdAt: Instant; updatedAt: Instant }
interface AuditEventDto { id: Id; repositoryId: Id; actorType: ActorType; actorUserId: Id | null; actorCredentialId: Id | null; operation: string; targetType: "REPOSITORY" | "SCAN" | "FINDING" | "INCIDENT" | "CI_CREDENTIAL"; targetId: Id; safeChangeSummary: string; occurredAt: Instant }
interface NotificationDto { id: Id; incidentId: Id; auditEventId: Id; recipientConfigReference: string; status: "QUEUED" | "SENDING" | "SENT" | "FAILED"; attemptCount: number; safeErrorCode: string | null; createdAt: Instant; updatedAt: Instant }
interface GateDto { repositoryId: Id; commitSha: string; scanId: Id | null; outcome: "ALLOW" | "BLOCK"; reason: "CLEAN" | "FINDINGS_DETECTED" | "SCAN_FAILED" | "SCAN_PENDING" | "NO_SCAN" }
interface DashboardRecentActivity { id: Id; repositoryId: Id; operation: string; actorType: ActorType; safeChangeSummary: string; occurredAt: Instant }
interface RepositorySecuritySummary { repositoryId: Id; githubOwner: string; githubName: string; active: boolean; latestScanId: Id | null; latestCommitSha: string | null; latestScanStatus: ScanStatus | null; gateOutcome: "ALLOW" | "BLOCK"; gateReason: GateDto["reason"] }
interface DashboardOverviewDto { repositories: number; activeRepositories: number; scans: number; findings: number; incidents: number; scanOutcomes: Record<ScanStatus, number>; findingsBySeverity: Record<Severity, number>; incidentsByStatus: Record<IncidentStatus, number>; failedNotifications: number; recentActivity: DashboardRecentActivity[]; repositorySecurity: RepositorySecuritySummary[] }
```

Synthetic incident transition example (optimistic version checked server-side):

```json
{"status":"RESOLVED","reason":"Synthetic fixture was removed and the demo credential rotated.","version":2}
```

Dashboard counts use the same authorized repositories and date window; findings
count occurrences, incidents count deduplicated records. Severity/status maps always
contain every enum key with zero when empty.
`GET /api/dashboard/overview` accepts optional `from` (inclusive) and `to`
(exclusive) UTC instants. The time window filters scans, finding occurrences,
incident creation, failed delivery creation, and recent audit activity. Repository
and active-repository counts are current inventory. Recent activity and repository
security summaries return at most 10 rows each; repository gate status uses the
same validated gate decision as `/gate`, so a malformed CLEAN record is blocked.
Audit `operation` is one of
`REPOSITORY_CREATED`, `REPOSITORY_UPDATED`, `REPOSITORY_DEACTIVATED`, `SCAN_CREATED`,
`SCAN_STARTED`, `SCAN_COMPLETED`, `SCAN_FAILED`, `INCIDENT_CREATED`, `INCIDENT_RESOLVED`,
`INCIDENT_IGNORED`, `INCIDENT_REOPENED`, `INCIDENT_OCCURRENCE`,
`CI_CREDENTIAL_CREATED`, `CI_CREDENTIAL_REVOKED`.
The event identity for notifications is the audit event ID; no repeated import
creates a new audit/delivery event for an already completed identical scan.
Each HIGH/CRITICAL finding occurrence queues one delivery per configured recipient
alias in the scan transaction; sending starts only after commit. The unique audit-event/alias key prevents
duplicate records. Delivery uses at most three attempts with 30-second and
120-second retry delays. `SENT` means the provider accepted the operation; it does
not prove arrival in the recipient's inbox. The response exposes only the alias,
status, attempt count and a fixed safe error code, never the recipient address.

M02 verification: normalized registration/login, username/password limits, safe /me, unique-constraint races, expired/tampered/unknown-user JWTs, removal of in-memory/Basic/form authentication and exact-origin CORS passed in the 70-test isolated suite. See [authentication validation and session policy](auth-session.md) and [manual checks](auth-manual-checks.md).
