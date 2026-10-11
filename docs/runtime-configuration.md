# Runtime configuration and liveness

M01.4 adds automatic user timestamps, an explicit Open EntityManager in View policy,
local/test configuration, and a public liveness endpoint. Database migrations and
isolated database test execution remain M01.2 and M01.5.

## Profiles and environment

The default profile is `local`. Its checked-in `application-local.yml` contains only
shared settings and environment references. Put private credentials in process
environment variables, not in this file. `.env` is still ignored and is not loaded
automatically by Spring Boot. The exact shared classpath profile file is exempted
from `.gitignore`; machine-specific `application-local.yml` files elsewhere stay ignored.

| Configuration | Database | Schema policy | JWT secret |
| --- | --- | --- | --- |
| `local` (default) | `DB_URL` and `DB_USERNAME` have the existing localhost/postgres defaults; `DB_PASSWORD` is required | Existing `update` behavior, pending M01.2 | Required `JWT_SECRET` |
| `test` (test classpath only) | Required `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`; no fallback to development `DB_*` | `none`; no automatic schema changes | Synthetic test-only value; Spring tests pin this property to avoid shell overrides |
| Other explicitly selected profiles | Required `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Common default `none` | Required `JWT_SECRET` |

To select local configuration explicitly when starting from a configured PowerShell terminal:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

Deployment must select its own profile explicitly; the default is for local development.
No deployment profile or database migration is introduced by this step.
Profile loading follows [Spring Boot profile configuration](https://docs.spring.io/spring-boot/reference/features/profiles.html).

`spring.jpa.open-in-view` is explicitly `false` in common configuration and tests.
Persistence work and any required lazy loading belong inside service transactions;
controllers should return safe DTOs. Hibernate's JDBC timezone is UTC and SQL logging
remains disabled.

## User timestamps

JPA `@PrePersist` initializes `createdAt` and `updatedAt` to the same UTC time.
`@PreUpdate` refreshes only `updatedAt`. Registration no longer assigns timestamps.
`createdAt` is not updatable by Hibernate; neither timestamp has a public setter.

The existing `LocalDateTime` storage type is retained. New values represent UTC;
this step does not reinterpret historical timestamps recorded in local time.
`updatedAt` is nullable so local Hibernate schema updates can add it without losing
existing rows. Legacy rows receive an update timestamp on their next JPA update;
backfill and final constraints belong to M01.2. JPA callbacks do not cover bulk SQL
or JPQL updates, which should not be used to bypass this lifecycle policy.

## Liveness

Public `GET /api/health/live` returns HTTP 200 and only:

```json
{"status":"UP"}
```

It performs no database, JWT, scanner, or external-service checks and discloses no
versions, credentials, configuration, or dependency details. The JWT filter skips
this exact GET route, including when a bearer header is supplied. Other methods and
neighboring paths do not inherit this permission. It also works under a servlet context path.

This is process liveness, not database readiness. Full application startup still
requires valid database/JWT configuration. `/api/health` retains its existing protected
behavior, and all other protected routes continue to require authentication.

## Verification

```powershell
.\mvnw.cmd -o '-Dtest=ApiErrorHandlingTests,AuthServiceConflictTests,LivenessTests,UserTimestampTests,ProfileConfigurationTests' test
```

All 36 focused tests passed on 11 October 2026 using Java 26.0.2 and the existing
Java 21 compilation target. They cover timestamp callback behavior, default local
configuration, test configuration without development-variable fallback, disabled
Open EntityManager in View, exact liveness output and database/JWT independence,
and protected-route/error-response regression checks. `git diff --check` also passed.

The original `SecretShieldApplicationTests` now explicitly selects `test`, so it
requires dedicated `TEST_DB_*` inputs. It was not run in this step. No development
database was started or modified during those focused tests, and callback persistence/database startup integration
has not yet been verified; that requires the isolated database setup in M01.5.

## Additional local smoke check — 11 October 2026

After the local JWT secret was replaced, reran all 36 focused tests successfully.
Confirmed `.env` is ignored/untracked and the private signing key meets the current
32-byte minimum without printing its value. The inherited process JWT setting did
not match `.env`; temporary startup explicitly loaded the four approved backend
variables from the private file, without printing them.

An initial startup with schema updates disabled succeeded, but a read-only login
for a synthetic nonexistent user returned 500: PostgreSQL reported missing
`updated_at` (SQLState `42703`). Normal local startup using the existing `update`
setting then synchronized the schema; the same login check returned the expected
JSON 401. This used the existing local schema-update path, not versioned migrations.
No users were registered or modified by these checks.

Live checks passed for minimal liveness (200, also with an invalid bearer header),
protected health (401), malformed JSON (400), invalid email (400), and nonexistent-user
login (401), with the documented JSON response shapes. Both temporary servers were
stopped afterwards. Successful login, persisted timestamp behavior, real-token
expiry/tampering, and the database-backed context test were not checked here.

For future starts, update the terminal's `JWT_SECRET` as well as `.env`; changing
the private file does not replace an already inherited environment variable.
