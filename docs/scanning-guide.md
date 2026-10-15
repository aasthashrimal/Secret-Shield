# Local Gitleaks scanning

M06 runs Gitleaks 8.30.0 against repositories already present under one configured,
allowlisted workspace. The backend never accepts a filesystem path or command from
an API request. A repository record selects only
`SCANNER_WORKSPACE/<githubOwner>/<githubName>`, and its requested branch and commit
must exactly match that checkout's current branch and `HEAD`.

## Install the pinned scanner

From the project root on Windows, run:

```powershell
powershell -NoProfile -File .\scripts\install-gitleaks.ps1
$env:GITLEAKS_PATH = (Resolve-Path '.\target\tools\gitleaks\8.30.0\gitleaks.exe').Path
```

The installer downloads the [official Windows x64 v8.30.0 archive](https://github.com/gitleaks/gitleaks/releases/tag/v8.30.0), verifies the
pinned SHA-256 checksum, checks the reported version, and installs it only under
the ignored `target/tools` directory. The project deliberately does not use
v8.30.1 because its Windows release has a reported [checksum mismatch](https://github.com/gitleaks/gitleaks/issues/2164)
and a reported [detection regression](https://github.com/gitleaks/gitleaks/issues/2170).

For normal startup, put the executable path in your private `.env`:

```text
GITLEAKS_PATH=C:\absolute\path\to\gitleaks.exe
SCANNER_WORKSPACE=target/scanner-workspace
GITLEAKS_CONFIG_PATH=config/gitleaks.toml
SCANNER_TIMEOUT_SECONDS=60
```

Do not commit `.env`, raw reports, scanner logs, real credentials, or repositories
created to reproduce leaks.

## Create safe test repositories

```powershell
powershell -NoProfile -File .\scripts\prepare-scanner-fixtures.ps1
```

The script recreates two ignored repositories:

- `target/scanner-workspace/fixture-owner/clean-fixture`
- `target/scanner-workspace/fixture-owner/finding-fixture`

It prints each current commit SHA. The finding fixture contains an unmistakably
synthetic marker matched by `secret-shield-synthetic` in `config/gitleaks.toml`.
It is not a usable credential and exists only in ignored generated Git history.

Run the opt-in adapter verification:

```powershell
$env:GITLEAKS_REAL_TEST = 'true'
$env:GITLEAKS_PATH = (Resolve-Path '.\target\tools\gitleaks\8.30.0\gitleaks.exe').Path
.\mvnw.cmd -Dtest=GitleaksRealFixtureTests test
```

The verified safe results are:

```text
clean-fixture: status CLEAN; scannerVersion 8.30.0; findings 0
finding-fixture: status FINDINGS_DETECTED; rule secret-shield-synthetic; file fixture.txt; line 1
```

No raw match, secret value, source snippet, scanner stdout, or scanner stderr is
included in normalized results.

## Run through the API

Start the backend normally after loading `.env`. Register or log in, then keep the
returned JWT only in a shell variable:

```powershell
$base = 'http://localhost:8080'
$login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" `
  -ContentType 'application/json' `
  -Body (@{ email = 'your-account@example.com'; password = 'your-password' } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($login.token)" }
```

Use one fixture name and the commit printed by the fixture script:

```powershell
$repository = Invoke-RestMethod -Method Post -Uri "$base/api/repositories" `
  -Headers $headers -ContentType 'application/json' `
  -Body (@{ githubOwner='fixture-owner'; githubName='clean-fixture'; defaultBranch='main' } | ConvertTo-Json)

$commit = (& git -C 'target/scanner-workspace/fixture-owner/clean-fixture' rev-parse HEAD).Trim()
$scan = Invoke-RestMethod -Method Post -Uri "$base/api/repositories/$($repository.id)/scans" `
  -Headers $headers -ContentType 'application/json' `
  -Body (@{ branch='main'; commitSha=$commit } | ConvertTo-Json)

$result = Invoke-RestMethod -Method Post `
  -Uri "$base/api/repositories/$($repository.id)/scans/$($scan.id)/start" `
  -Headers $headers
$result | Select-Object id,status,scannerVersion,failureCode
```

Repeat with `finding-fixture` to observe `FINDINGS_DETECTED`. The start request is
currently synchronous: HTTP 202 is returned after the bounded local process has
finished and the returned `ScanDto` is already terminal. A second start for the
same scan returns 409.

## Boundaries and failures

The adapter scans only the selected `HEAD` commit (`git log -1 <commit>`), rather
than cloning repositories or scanning arbitrary paths. It uses an argument array,
a fixed configuration, a process timeout, 5 MiB report and 64 KiB stdout/stderr
limits, and a unique temporary directory per scan. It deletes the raw report as
soon as it has extracted safe rule/file/line metadata and cleans the temporary
directory after every outcome.

Controlled failures set the scan to `FAILED` with a fixed code such as
`SCANNER_SOURCE_MISSING`, `SCANNER_IDENTITY_MISMATCH`, `SCANNER_MISSING`,
`SCANNER_VERSION_MISMATCH`, `SCANNER_TIMEOUT`, `SCANNER_OUTPUT_LIMIT`,
`SCANNER_REPORT_INVALID`, or `SCANNER_PROCESS_ERROR`. Failure responses and logs do
not include tool output, paths, report contents, or supplied secret values.
