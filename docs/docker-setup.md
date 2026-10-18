# Docker local stack (M13)

Run these commands in PowerShell from the repository root. Docker Desktop must be
running in **Linux containers** mode. The Compose stack has its own PostgreSQL
database and does not use your existing `.env`, Neon database, or Windows PostgreSQL
service. It starts a backend on `http://localhost:8080` and a frontend on
`http://localhost:5173`. The frontend sends `/api` requests to the backend through
Nginx, so browser login works on one origin.

## First start

1. Start Docker Desktop and wait until its engine says it is running. Check with
   `docker info --format '{{.ServerVersion}}'`.
2. Create a private Compose environment file:

   ```powershell
   powershell -NoProfile -File .\scripts\init-compose-env.ps1
   ```

   This creates `.env.compose` only if it does not exist, with separate random
   database and JWT values. It is ignored by Git. Do not share or commit it.
3. Prepare the two local Git repositories used for scanner checks:

   ```powershell
   powershell -NoProfile -File .\scripts\prepare-scanner-fixtures.ps1
   ```

   This replaces only `target/scanner-workspace/fixture-owner` and prints the new
   fixture commit SHAs. It does not touch your real repositories. For the first
   stack start, this step is optional; it is required before manual scan checks.
4. Build and start all services:

   ```powershell
   docker compose --env-file .env.compose up -d --build
   docker compose --env-file .env.compose ps
   ```

   Wait for `db`, `backend`, and `frontend` to show `healthy`. The first build
   downloads base images and dependencies. Flyway creates the schema at backend
   startup. Open `http://localhost:5173` and create an account there. This is a
   separate local database; an account in Neon will not automatically exist here.
5. Check the backend through the frontend proxy:

   ```powershell
   Invoke-RestMethod http://localhost:5173/api/health/live
   ```

   Expected: `status` is `UP`. The same path on port 8080 checks the backend
   directly. Only loopback ports 5173 and 8080 are published; PostgreSQL stays on
   the Compose network.

## Verify clean and finding scans

For an automated check that also verifies restart/persistence, run:

```powershell
powershell -NoProfile -File .\scripts\verify-compose.ps1
```

This creates one synthetic account and two fixture repository records in the
disposable Compose database, stops and restarts the stack without deleting its
volume, and prints only PASS lines. It requires the two fixture Git repositories.

For a manual check, use an account you registered in the Compose browser.
Run these commands in one PowerShell window. Type your own email/password at the
prompts; the token remains in a shell variable.

```powershell
$base = 'http://localhost:5173'
$email = Read-Host 'Compose account email'
$password = Read-Host 'Compose account password' -AsSecureString
$plainPassword = [Net.NetworkCredential]::new('', $password).Password
$login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" `
  -ContentType 'application/json' `
  -Body (@{ email = $email; password = $plainPassword } | ConvertTo-Json)
Remove-Variable plainPassword
$headers = @{ Authorization = "Bearer $($login.token)" }

foreach ($name in @('clean-fixture', 'finding-fixture')) {
  $page = Invoke-RestMethod -Uri "$base/api/repositories?size=100" -Headers $headers
  $repository = $page.content | Where-Object { $_.githubOwner -eq 'fixture-owner' -and $_.githubName -eq $name } | Select-Object -First 1
  if (-not $repository) {
    $repository = Invoke-RestMethod -Method Post -Uri "$base/api/repositories" `
      -Headers $headers -ContentType 'application/json' `
      -Body (@{ githubOwner = 'fixture-owner'; githubName = $name; defaultBranch = 'main' } | ConvertTo-Json)
  }
  $commit = (& git -C "target/scanner-workspace/fixture-owner/$name" rev-parse HEAD).Trim()
  $scan = Invoke-RestMethod -Method Post -Uri "$base/api/repositories/$($repository.id)/scans" `
    -Headers $headers -ContentType 'application/json' `
    -Body (@{ branch = 'main'; commitSha = $commit } | ConvertTo-Json)
  $result = Invoke-RestMethod -Method Post `
    -Uri "$base/api/repositories/$($repository.id)/scans/$($scan.id)/start" `
    -Headers $headers
  $result | Select-Object id, status, scannerVersion, failureCode
}
```

Expected: `CLEAN` followed by `FINDINGS_DETECTED`, both using scanner version
`8.30.0`. In the browser, refresh the repository pages to see the scans and the
synthetic finding. The fixture scripts create new commits each time; the commands
reuse an existing fixture repository record when present.
Only the backend image includes Git and Gitleaks. It reads the fixture directory
through a read-only bind mount and never needs a Docker socket.

## Persistence and shutdown

To verify persistence, register a test account or create a repository, then run:

```powershell
docker compose --env-file .env.compose down
docker compose --env-file .env.compose up -d
```

Log back in and confirm the account/repository is still there. `down` stops and
removes the containers and network; it **keeps** the named PostgreSQL volume.
Use the same `down` command for ordinary shutdown. `docker compose stop` also
keeps containers and data. Keep `.env.compose` unchanged while reusing the volume:
PostgreSQL's initialized role password stays tied to that volume.

**Data reset only, when you explicitly want to erase this Compose database:**

```powershell
docker compose --env-file .env.compose down --volumes
```

This deletes all accounts, scans, findings, and incidents in the Compose database.
It does not delete Neon data or the host fixture directory. Do not run this for
normal shutdown. For a backup before reset, use `pg_dump`/`pg_restore` as described
in [database migrations](database-migrations.md).

## Troubleshooting and hosting boundary

- `docker info` cannot connect: start Docker Desktop and select Linux containers.
- Compose says a variable is missing: run `init-compose-env.ps1` and pass
  `--env-file .env.compose` to every Compose command.
- Backend is unhealthy: use `docker compose --env-file .env.compose logs backend`.
  Database password and JWT are runtime variables; do not paste raw logs if they
  contain private values. Check whether another process owns port 8080.
- Frontend cannot start: check whether another process owns port 5173. The Nginx
  proxy needs the backend to become healthy first.
- Scanner returns `SCANNER_SOURCE_MISSING`: prepare fixtures before starting the
  backend or restart it after creating the bind-mount source directory. The backend
  only reads `target/scanner-workspace`.
- Scanner returns `SCANNER_IDENTITY_MISMATCH`: rerun `git rev-parse HEAD` for the
  exact fixture branch and create a new scan for that commit.
- After changing `.env.compose` database password, an old volume still has the
  original PostgreSQL password. Restore the old value or intentionally reset the
  disposable volume after backing up any wanted data.

The backend Dockerfile is the image used by M12's allowed-path image build and is
suited to a single-container Azure App Service backend. The frontend is a separate
static Nginx image for local Compose; M14 will choose its Azure hosting target.
M15 adds cloud deployment only after the trusted scan and build gates.

## Verification record (18 October 2026)

On the Member 1 Windows machine, both images built, all three services became
healthy, and Flyway applied V1–V9 to the fresh Compose database. An Edge browser
registered and logged in through the container frontend. The repeatable check
passed a real Gitleaks `CLEAN` scan, a real `FINDINGS_DETECTED` scan, and login plus
repository persistence after `docker compose down`/`up`. The first Linux scan
exposed a `v8.30.0` version-prefix mismatch in the adapter; that was fixed and the
full check passed on rerun. Teammate fresh-clone reproduction is still pending.
The default Java 21 suite passed 78 tests with four opt-in scanner tests skipped;
the frontend suite passed five tests.
