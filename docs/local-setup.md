# Local backend setup

This guide covers Modules 0 and 1 on Windows for Member 1. Commands were verified
on this machine; teammate/GitHub setup is deferred. Run commands from the repository root.

## Prerequisites

Install Java 21 JDK and PostgreSQL server tools (`psql`, `pg_dump`, `initdb`,
`pg_ctl`, `createdb`). The existing Maven wrapper downloads Maven and dependencies
on first use; a separate Maven installation is unnecessary. Docker is unnecessary
for the current backend/tests. The Java and PostgreSQL paths may differ on your machine.

```powershell
$env:JAVA_HOME = 'C:\path\to\your\jdk-21'
& "$env:JAVA_HOME\bin\java.exe" -version
.\mvnw.cmd -version
```

For this checkout's verification, an official Microsoft Java 21 ZIP was checksum
verified and extracted under ignored `target/tools/java21`. If that directory is
still present, you can use:

```powershell
$env:JAVA_HOME = (Resolve-Path 'target/tools/java21/jdk-21.0.12.1+1').Path
```

This portable copy is temporary: `mvnw clean` removes it. Prefer a permanent JDK
installation for ongoing work. [Official Microsoft downloads](https://learn.microsoft.com/en-us/java/openjdk/download).

## Private environment file

If `.env` already exists, retain it. Otherwise:

```powershell
Copy-Item .env.example .env
```

Edit `.env` privately. `DB_PASSWORD=` takes the actual PostgreSQL role password;
`JWT_SECRET=` takes the actual random signing secret, not another variable name.
Set `DB_URL=jdbc:postgresql://localhost:5432/secret_shield_db` and
`DB_USERNAME=postgres` (or your dedicated role). Use at least 32 UTF-8 bytes for
JWT_SECRET; a random 32-byte Base64 value is suitable. To generate it locally:

```powershell
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($bytes); [Convert]::ToBase64String($bytes) } finally { $rng.Dispose() }
```

Copy the displayed value only into your private `.env`. Never paste credentials,
JWTs, logs containing tokens, or database backups into commits or screenshots.
Keep the same key across restarts unless intentionally invalidating existing tokens.

The startup script reads only the four core backend values and optional FRONTEND_ORIGIN as literal strings,
without evaluating shell code. It replaces stale inherited values for its child
process. It accepts optional matching outer quotes; inline comments and variable
substitution are not supported. Spring Boot does not load `.env` when started directly
through Maven or an IDE. For IDE startup, configure the same core environment values and optional FRONTEND_ORIGIN
privately in the run configuration.

## Database and startup

Start your existing PostgreSQL Windows service. For a **fresh database only**, create
the database once (skip if it already exists):

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\createdb.exe' -h localhost -p 5432 -U postgres -W secret_shield_db
```

The password prompt is PostgreSQL's role password. The app role must be able to
create/alter its tables. A fresh database gets V1/V2 migrations automatically:

```powershell
powershell -NoProfile -File .\scripts\start-local.ps1
```

For a database containing the earlier Hibernate-created `users` table and no
Flyway history, perform explicit adoption **once**:

```powershell
powershell -NoProfile -File .\scripts\start-local.ps1 -AdoptExistingDatabase
```

This backs up the configured database before startup, verifies the supported schema,
baselines at version 0, applies V1/V2 without deleting users, and starts the app.
Copy the backup to a private safe location before running `mvnw clean`, which removes
`target/`. See [migration details and recovery](database-migrations.md).
After successful adoption, use ordinary startup without the switch. Do not enable
adoption permanently in shared configuration.

The default port is 8080. Use `-Port 8081` if necessary. `-Offline` is available
when Maven dependencies are cached. `-PostgresBin 'C:\path\to\PostgreSQL\bin'`
selects backup tools for a nonstandard installation. Stop the backend with Ctrl+C.

## Check what exists now

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd test
powershell -NoProfile -File .\scripts\test-postgres.ps1
```

Expected: successful compilation, 45 database-independent tests, and 70 tests in
the full disposable PostgreSQL run. The latter includes positive registration/login,
BCrypt storage, valid JWT access, timestamps, conflicts and fresh/legacy migrations.
It uses synthetic data and cleans up its own database; development data is untouched.

With the backend running, open another terminal:

```powershell
Invoke-RestMethod http://localhost:8080/api/health/live
powershell -NoProfile -File .\scripts\check-local.ps1
```

Expected: `status: UP` and seven PASS lines. The smoke script verifies minimal
liveness and safe 400/401 responses without creating users. Successful registration
and login are covered by the isolated full suite, which does not leave demo accounts.

Implemented routes include `POST /api/auth/register` (201), `POST /api/auth/login`
(200 with JWT), `GET /api/health/live` (public 200), and `GET /api/health`
(200 with JWT, otherwise 401). See [request/response contract](api-contract.md).
`GET /api/auth/me` now returns the safe current profile with a valid bearer token. Repositories and scanning routes belong to later modules.

## Troubleshooting

- Wrong Java: inspect `mvnw -version`; set JAVA_HOME to the JDK directory, not `bin`.
- Connection refused: start PostgreSQL and verify the private DB_URL host/port/name.
- Password authentication failed: DB_PASSWORD must match DB_USERNAME's PostgreSQL password.
- Missing/short JWT secret: correct the private `.env`; restarting loads the new value.
- Existing schema needs adoption: use the backup/adoption command; never delete tables to bypass it.
- Incompatible schema or Flyway validation failed: stop and inspect migration history/schema;
  do not edit already-applied migration files or blindly run repair.
- Maven offline dependency error: omit `-Offline`/`-o` for the first run.
- Isolated test tooling/cleanup issues: see [testing](testing.md); do not point tests at development data.

M02 now removes default in-memory authentication and implements the bearer/CORS session policy. See [authentication policy](auth-session.md) and [manual checks](auth-manual-checks.md). Structured roles and resource authorization remain M03.
