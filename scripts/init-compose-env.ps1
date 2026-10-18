$ErrorActionPreference = 'Stop'
$path = Join-Path (Split-Path $PSScriptRoot -Parent) '.env.compose'
if (Test-Path -LiteralPath $path) {
    Write-Host '.env.compose already exists; keeping its current credentials.'
    exit 0
}

$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $dbBytes = New-Object byte[] 32
    $jwtBytes = New-Object byte[] 48
    $rng.GetBytes($dbBytes)
    $rng.GetBytes($jwtBytes)
    $dbPassword = [BitConverter]::ToString($dbBytes).Replace('-', '').ToLowerInvariant()
    $jwtSecret = [BitConverter]::ToString($jwtBytes).Replace('-', '').ToLowerInvariant()
    $content = "COMPOSE_DB_PASSWORD=$dbPassword`nCOMPOSE_JWT_SECRET=$jwtSecret`n"
    [IO.File]::WriteAllText($path, $content, (New-Object Text.UTF8Encoding $false))
    Write-Host 'Created ignored .env.compose with random local-only credentials.'
} finally {
    $rng.Dispose()
}
