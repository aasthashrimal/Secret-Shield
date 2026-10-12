[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Email,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
. (Join-Path $PSScriptRoot 'local-environment.ps1')
Import-LocalEnvironment -Workspace $workspace
$normalized = $Email.Trim().ToLowerInvariant()
if ($normalized.Length -gt 255 -or $normalized -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$') {
    throw 'Supply the normalized email of an existing registered user.'
}
$names = @('ADMIN_BOOTSTRAP_ENABLED', 'ADMIN_BOOTSTRAP_EMAIL', 'ADMIN_BOOTSTRAP_EXIT')
$saved = @{}
foreach ($name in $names) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $env:ADMIN_BOOTSTRAP_ENABLED = 'true'
    $env:ADMIN_BOOTSTRAP_EMAIL = $normalized
    $env:ADMIN_BOOTSTRAP_EXIT = 'true'
    Push-Location $workspace
    try {
        $mavenArgs = @('spring-boot:run',
            '-Dspring-boot.run.profiles=local',
            '-Dspring-boot.run.arguments=--spring.main.web-application-type=none')
        if ($Offline) { $mavenArgs = @('-o') + $mavenArgs }
        & (Join-Path $workspace 'mvnw.cmd') @mavenArgs
        if ($LASTEXITCODE -ne 0) { throw 'Administrator bootstrap failed; no success is claimed.' }
    } finally { Pop-Location }
} finally {
    foreach ($name in $names) {
        [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process')
    }
}
