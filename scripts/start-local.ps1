[CmdletBinding()]
param(
    [switch]$AdoptExistingDatabase,
    [switch]$Offline,
    [int]$Port = 8080,
    [string]$PostgresBin = $env:PG_BIN
)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
. (Join-Path $PSScriptRoot 'local-environment.ps1')
Import-LocalEnvironment -Workspace $workspace
if ($Port -lt 1 -or $Port -gt 65535) { throw 'Invalid application port.' }
$arguments = "--server.port=$Port"
if ($AdoptExistingDatabase) {
    $dbUrl = if ($env:DB_URL) { $env:DB_URL } else { 'jdbc:postgresql://localhost:5432/secret_shield_db' }
    if ($dbUrl -notmatch '^jdbc:postgresql://([A-Za-z0-9.-]+):([0-9]{1,5})/([A-Za-z0-9_]+)$') {
        throw 'Backup runner requires an explicit PostgreSQL host:port/database URL without credentials or query options.'
    }
    $dbHost = $Matches[1]; $dbPort = $Matches[2]; $dbName = $Matches[3]
    if (-not $PostgresBin) {
        $tool = Get-Command psql -ErrorAction SilentlyContinue
        if ($tool) { $PostgresBin = Split-Path $tool.Source }
        elseif ($env:ProgramFiles) {
            $versions = @(Get-ChildItem (Join-Path $env:ProgramFiles 'PostgreSQL') -Directory -ErrorAction SilentlyContinue |
                Sort-Object { [version]$_.Name } -Descending)
            foreach ($version in $versions) {
                $candidate = Join-Path $version.FullName 'bin'
                if (Test-Path (Join-Path $candidate 'pg_dump.exe')) { $PostgresBin = $candidate; break }
            }
        }
    }
    if (-not $PostgresBin -or -not (Test-Path (Join-Path $PostgresBin 'pg_dump.exe'))) {
        throw 'pg_dump is required for the pre-adoption backup; supply -PostgresBin.'
    }
    $backupRoot = Join-Path $workspace ('target/database-backups/' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $backupRoot | Out-Null
    $backupFile = Join-Path $backupRoot 'before-adoption.dump'
    $previousPassword = $env:PGPASSWORD
    try {
        $env:PGPASSWORD = $env:DB_PASSWORD
        $dbUser = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'postgres' }
        & (Join-Path $PostgresBin 'pg_dump.exe') -h $dbHost -p $dbPort -U $dbUser -d $dbName -Fc -f $backupFile `
            *> (Join-Path $backupRoot 'backup.log')
        if ($LASTEXITCODE -ne 0 -or (Get-Item -LiteralPath $backupFile).Length -eq 0) {
            throw 'Database backup failed; adoption has not started.'
        }
    } finally { $env:PGPASSWORD = $previousPassword }
    Write-Host ('Pre-adoption backup saved under ' + $backupRoot)
    $arguments += ' --secretshield.database.adopt-existing=true'
}
Push-Location $workspace
try {
    $mavenArgs = @('spring-boot:run', '-Dspring-boot.run.profiles=local', "-Dspring-boot.run.arguments=$arguments")
    if ($Offline) { $mavenArgs = @('-o') + $mavenArgs }
    & (Join-Path $workspace 'mvnw.cmd') @mavenArgs
    exit $LASTEXITCODE
} finally { Pop-Location }
