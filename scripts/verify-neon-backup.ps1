param(
    [string]$EnvFile = (Join-Path (Split-Path $PSScriptRoot -Parent) '.env'),
    [switch]$ValidateOnly,
    [switch]$KeepBackup
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-DockerPrivate {
    param([Parameter(Mandatory)][string[]]$Arguments)

    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& docker @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    [pscustomobject]@{ ExitCode = $exitCode; Output = $output }
}

$workspace = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
$targetRoot = [IO.Path]::GetFullPath((Join-Path $workspace 'target'))
$envPath = [IO.Path]::GetFullPath($EnvFile)

if (!(Test-Path -LiteralPath $envPath -PathType Leaf)) {
    throw 'Private environment file was not found.'
}

& git -C $workspace check-ignore -q -- $envPath
if ($LASTEXITCODE -ne 0) {
    throw 'The private environment file must be Git-ignored before backup verification.'
}

$values = @{}
foreach ($line in [IO.File]::ReadAllLines($envPath)) {
    if ($line -match '^\s*(DB_URL|DB_USERNAME|DB_PASSWORD)\s*=(.*)$') {
        if ($values.ContainsKey($Matches[1])) {
            throw "Duplicate $($Matches[1]) entry in the private environment file."
        }
        $values[$Matches[1]] = $Matches[2].Trim()
    }
}

foreach ($name in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace($values[$name])) {
        throw "Private environment is missing $name."
    }
}

if (!$values.DB_URL.StartsWith('jdbc:postgresql://', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'DB_URL must be a PostgreSQL JDBC URL.'
}

try {
    $databaseUri = [Uri]$values.DB_URL.Substring(5)
} catch {
    throw 'DB_URL could not be parsed safely.'
}

$databaseName = [Uri]::UnescapeDataString($databaseUri.AbsolutePath.TrimStart('/'))
if ([string]::IsNullOrWhiteSpace($databaseUri.Host) -or
        [string]::IsNullOrWhiteSpace($databaseName) -or
        $databaseName.Contains('/')) {
    throw 'DB_URL must contain one host and database name.'
}
$databasePort = if ($databaseUri.IsDefaultPort) { 5432 } else { $databaseUri.Port }

$dockerCheck = Invoke-DockerPrivate -Arguments @('version', '--format', '{{.Server.Version}}')
if ($dockerCheck.ExitCode -ne 0) {
    throw 'Docker Desktop must be running for the disposable restore check.'
}
$dockerCheck = $null

if ($ValidateOnly) {
    [pscustomobject]@{
        EnvIgnored          = $true
        ConnectionValidated = $true
        DockerAvailable     = $true
        CloudReadPerformed  = $false
        BackupCreated       = $false
    }
    exit 0
}

$runId = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ') + '-' +
    [Guid]::NewGuid().ToString('N').Substring(0, 8)
$backupRoot = [IO.Path]::GetFullPath((Join-Path $targetRoot "database-backups/cloud-$runId"))
$dumpPath = Join-Path $backupRoot 'secret-shield.dump'
$sourceEnv = Join-Path $backupRoot '.source.env'
$restoreEnv = Join-Path $backupRoot '.restore.env'
$container = 'secret-shield-restore-' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
$containerStarted = $false
$verified = $false

New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
try {
    [IO.File]::WriteAllLines($sourceEnv, @(
        "PGPASSWORD=$($values.DB_PASSWORD)",
        'PGSSLMODE=require'
    ), [Text.UTF8Encoding]::new($false))

    $dumpResult = Invoke-DockerPrivate -Arguments @(
        'run', '--rm',
        '--env-file', $sourceEnv,
        '--mount', "type=bind,source=$backupRoot,target=/backup",
        'postgres:18-alpine',
        'pg_dump', '--host', $databaseUri.Host, '--port', [string]$databasePort,
        '--username', $values.DB_USERNAME, '--dbname', $databaseName,
        '--format', 'custom', '--no-owner', '--no-privileges',
        '--file', '/backup/secret-shield.dump'
    )
    if ($dumpResult.ExitCode -ne 0) {
        $dumpResult = $null
        throw 'Cloud database backup failed; private provider output was suppressed.'
    }
    $dumpResult = $null
    Remove-Item -LiteralPath $sourceEnv -Force

    if (!(Test-Path -LiteralPath $dumpPath -PathType Leaf) -or
            (Get-Item -LiteralPath $dumpPath).Length -le 0) {
        throw 'Cloud database backup was empty.'
    }

    $randomBytes = New-Object byte[] 24
    $randomGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $randomGenerator.GetBytes($randomBytes)
    } finally {
        $randomGenerator.Dispose()
    }
    $restorePassword = [Convert]::ToBase64String($randomBytes)
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
    [IO.File]::WriteAllLines($restoreEnv, @("POSTGRES_PASSWORD=$restorePassword"),
        [Text.UTF8Encoding]::new($false))
    $restorePassword = $null

    $containerResult = Invoke-DockerPrivate -Arguments @(
        'run', '--detach', '--name', $container,
        '--env-file', $restoreEnv,
        '--mount', "type=bind,source=$backupRoot,target=/backup,readonly",
        'postgres:18-alpine'
    )
    if ($containerResult.ExitCode -ne 0) {
        $containerResult = $null
        throw 'Disposable recovery database did not start.'
    }
    $containerResult = $null
    $containerStarted = $true

    $ready = $false
    foreach ($attempt in 1..30) {
        $readyResult = Invoke-DockerPrivate -Arguments @(
            'exec', $container, 'pg_isready', '--username', 'postgres', '--dbname', 'postgres'
        )
        if ($readyResult.ExitCode -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (!$ready) {
        throw 'Disposable recovery database did not become ready.'
    }

    $createResult = Invoke-DockerPrivate -Arguments @(
        'exec', $container, 'createdb', '--username', 'postgres', 'secret_shield_recovery'
    )
    if ($createResult.ExitCode -ne 0) { throw 'Could not create the disposable recovery database.' }
    $createResult = $null

    $restoreResult = Invoke-DockerPrivate -Arguments @(
        'exec', $container, 'pg_restore',
        '--username', 'postgres', '--dbname', 'secret_shield_recovery',
        '--no-owner', '--no-privileges', '/backup/secret-shield.dump'
    )
    if ($restoreResult.ExitCode -ne 0) {
        $restoreResult = $null
        throw 'Backup restore failed; private restore output was suppressed.'
    }
    $restoreResult = $null

    $tableResult = Invoke-DockerPrivate -Arguments @(
        'exec', $container, 'psql', '--username', 'postgres',
        '--dbname', 'secret_shield_recovery', '--tuples-only', '--no-align',
        '--command', "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';"
    )
    $migrationResult = Invoke-DockerPrivate -Arguments @(
        'exec', $container, 'psql', '--username', 'postgres',
        '--dbname', 'secret_shield_recovery', '--tuples-only', '--no-align',
        '--command', 'SELECT count(*) FROM flyway_schema_history;'
    )
    $publicTables = ($tableResult.Output -join "`n").Trim()
    $migrations = ($migrationResult.Output -join "`n").Trim()
    if ($tableResult.ExitCode -ne 0 -or $migrationResult.ExitCode -ne 0 -or
            $publicTables -notmatch '^\d+$' -or
            $migrations -notmatch '^\d+$' -or [int]$publicTables -lt 1 -or [int]$migrations -lt 1) {
        throw 'Restored database did not contain the expected schema evidence.'
    }

    $verified = $true
    [pscustomobject]@{
        BackupBytes       = (Get-Item -LiteralPath $dumpPath).Length
        PublicTables      = [int]$publicTables
        FlywayMigrations  = [int]$migrations
        RestoreVerified   = $true
        BackupRetained    = [bool]$KeepBackup
        BackupLocation    = if ($KeepBackup) {
            "target/database-backups/cloud-$runId/secret-shield.dump"
        } else { $null }
        PrivateDataShown  = $false
    }
} finally {
    $values = $null
    if ($containerStarted -and $container.StartsWith('secret-shield-restore-', [StringComparison]::Ordinal)) {
        $removeResult = Invoke-DockerPrivate -Arguments @('rm', '--force', $container)
        $removeResult = $null
    }
    foreach ($secretFile in @($sourceEnv, $restoreEnv)) {
        if (Test-Path -LiteralPath $secretFile) {
            Remove-Item -LiteralPath $secretFile -Force
        }
    }
    if ((!$KeepBackup -or !$verified) -and (Test-Path -LiteralPath $backupRoot)) {
        $resolvedBackup = [IO.Path]::GetFullPath($backupRoot)
        if ($resolvedBackup.StartsWith($targetRoot + [IO.Path]::DirectorySeparatorChar,
                [StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $resolvedBackup -Recurse -Force
        }
    }
}
