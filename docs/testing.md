# Isolated PostgreSQL tests

M01.5 uses a fresh local PostgreSQL cluster for every integration run. It never
connects to the development database, reads `.env`, or requires development
credentials. Docker is not required. Flyway now migrates the disposable schema; Hibernate validates it.

## Windows prerequisites

- Java 21 JDK and the checked-in Maven wrapper.
- Windows PowerShell 5.1 or PowerShell 7.
- Installed PostgreSQL **server tools**: `initdb.exe`, `pg_ctl.exe`, `createdb.exe`,
  and `psql.exe` from the same installation. PostgreSQL 18.6 was verified here.
- Permission to start local processes and write under this checkout's `target/`.
  The normal Windows user needs no development database or administrator DB password.
- Network access for Maven's first dependency download, or an already populated cache.

Member 1 implements Modules 0/1 alone. Second-machine execution is outside the current scope and remains unverified. A future teammate can install these tools without sharing development secrets.

## Commands

From the repository root, run the database-independent unit/MVC tests:

```powershell
.\mvnw.cmd test
```

Run unit tests **and** PostgreSQL integration tests:

```powershell
powershell -NoProfile -File .\scripts\test-postgres.ps1
```

The runner first looks for `PG_BIN`, then `psql` on PATH, then the usual
`C:\Program Files\PostgreSQL\<version>\bin` installation directories. For a custom installation:

```powershell
powershell -NoProfile -File .\scripts\test-postgres.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\18\bin'
```

Once dependencies are cached, `-Offline` passes Maven's `-o` option:

```powershell
powershell -NoProfile -File .\scripts\test-postgres.ps1 -Offline
```

No permanent `TEST_DB_*` settings are needed. The runner sets them only for its
child process and restores inherited values afterwards. It temporarily removes
development database/JWT overrides. Do not manually point integration tests at an
existing database or add test secrets to `.env`.

`postgres-tests` is an explicit Maven profile that adds `*IT` classes to the usual
test selection. Direct `mvnw -Ppostgres-tests test` without the runner fails closed
because it has no verified disposable database. The old context-load test is now
`SecretShieldApplicationIT` and uses the same guarded support as all API integration tests.

## Isolation and reusable support

The runner creates `target/postgres-tests/<random-run-id>/data`, initializes a new
cluster, chooses a free non-default port, binds to `127.0.0.1`, and creates its own
`secretshield_test` role and `secret_shield_test_<run-id>` database. The cluster
uses trust authentication only for this short-lived loopback instance; credentials
are synthetic and it must not be reused as a development/deployment server.

Before Spring configures the datasource or Hibernate writes, `DisposablePostgres`
requires the exact loopback JDBC URL shape, dedicated role, random run ID, matching
database name, and a matching `test_support.test_run_marker` row. Development URLs,
default port 5432, JDBC options, missing credentials, and mismatched IDs are rejected.
Dynamic datasource properties override ambient/application settings.

`PostgresApiTest` provides the full application context, MockMvc, JSON POST/token
helpers, test-only signing configuration, and transaction rollback for each test.
MockMvc automatic failure dumps are disabled so response JWTs/password hashes are
not printed. Extend it from new `*IT` classes; use synthetic fixtures and keep
assertions from printing tokens, passwords, or hashes. HTTP uses MockMvc rather
than a listening web server, so no application port is reserved.

Only after the marker check succeeds does Flyway migrate the fresh public schema. Hibernate uses validate. The marker is in a separate test_support schema, outside the managed application schema. Migration tests create additional uniquely named schemas inside this verified disposable database and remove them afterwards.

The script stops only its own cluster and deletes only its verified run directory
in `finally`, including Maven failures. Cleanup refuses junctions/symbolic links,
out-of-workspace targets, reused non-PostgreSQL process IDs, and unsuccessful shutdown.
Failed setup logs stay under ignored `target/postgres-tests/`; Maven reports are
under ignored `target/surefire-reports/`. Logs contain only disposable/synthetic test data.

## Troubleshooting

- **Missing tool:** install the PostgreSQL server tools, or supply `-PostgresBin`.
- **`could not create restricted token` in Codex's Windows sandbox:** run this
  specific test script with approved execution outside the sandbox. Normal local
  PowerShell execution worked on this machine.
- **Maven dependencies unavailable offline:** omit `-Offline` to download them.
- **Port collision:** rerun; each run selects a new port and never reuses development port 5432.
- **Missing isolation settings/marker:** run through the script; never bypass the guard.
- **Hard interruption:** `finally` may not run if the process tree is forcibly killed.
  Inspect `target/postgres-tests/` for the retained 32-character run directory, then:

```powershell
powershell -NoProfile -File .\scripts\test-postgres.ps1 -CleanupRunId '<32-character-run-id>'
```

The cleanup command validates the directory and the recorded server identity, stops
that instance if still running, and removes only that run. If shutdown cannot be
confirmed it retains the data and reports a failure. Do not run `mvnw clean` or
delete `target/` while an integration run or retained temporary server is active.

## Verification — 11 October 2026

Verified on Windows with PostgreSQL 18.6, Java 21.0.12.1, and unchanged dependency versions:

- Default database-independent suite: 54 tests.
- Disposable runner: 86 tests passed (54 unit/MVC/guard/JWT/authorization tests and 32 context/API/migration/HTTP
  integration tests), with no failures or skipped tests.
- Integration tests verified database identity, empty user state before each test,
  transaction rollback, registration persistence/BCrypt storage, login and JWT
  access to a protected route, duplicate registration, PostgreSQL uniqueness, and
  actual JPA creation/update timestamps.
- Temporary server stopped and its run directory was removed after completion.
- Direct execution of `SecretShieldApplicationIT` without the runner was tested
  and rejected with the generic isolation-settings error before datasource setup.
- `git diff --check` passed. No development database was targeted or modified.

This is foundation test support and focused integration evidence, not completion
of later authentication, authorization, scanner, or other application modules.

M01.2 migration tests additionally verified fresh/repeated migration, explicit existing-user/hash preservation, nullable timestamp backfill, missing opt-in and incompatible-schema rejection.
