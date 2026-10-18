[CmdletBinding()]
param(
    [string]$EnvironmentFile,
    [string]$VaultName = 'secretshielddevzboc5kkv',
    [switch]$IncludeNotifications,
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($EnvironmentFile)) {
    $EnvironmentFile = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
}

if (-not (Test-Path -LiteralPath $EnvironmentFile -PathType Leaf)) {
    throw 'Private environment file was not found.'
}
$values = @{}
foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $EnvironmentFile))) {
    if ($line -match '^\s*(DB_URL|DB_USERNAME|DB_PASSWORD|JWT_SECRET|ACS_CONNECTION_STRING|ACS_SENDER_ADDRESS|NOTIFICATION_RECIPIENTS)\s*=(.*)$') {
        $key = $Matches[1]
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        if ($values.ContainsKey($key)) { throw "Duplicate $key entry in private environment file." }
        $values[$key] = $value
    }
}
foreach ($key in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET')) {
    if ([string]::IsNullOrWhiteSpace($values[$key])) { throw "Missing $key in private environment file." }
}
if ($values['DB_URL'] -notmatch '^jdbc:postgresql://([^/:?#]+)(?::[0-9]+)?/[^?#]+(?:\?.*)?$') {
    throw 'DB_URL must be a JDBC PostgreSQL URL.'
}
$dbHost = $Matches[1]
if ($dbHost -notmatch '(?i)(^|\.)neon\.tech$') {
    throw 'The ongoing Azure configuration requires the existing Neon database URL.'
}
if ($values['DB_URL'] -notmatch '(?i)([?&])sslmode=(require|verify-ca|verify-full)($|&)') {
    throw 'The Neon JDBC URL must require TLS with sslmode=require or stronger.'
}
if ([Text.Encoding]::UTF8.GetByteCount($values['JWT_SECRET']) -lt 32) {
    throw 'JWT_SECRET must have at least 32 UTF-8 bytes.'
}
if ($VaultName -notmatch '^[a-zA-Z][a-zA-Z0-9-]{2,23}$') { throw 'Invalid Key Vault name.' }

$secrets = @{
    'db-url' = $values['DB_URL']
    'db-username' = $values['DB_USERNAME']
    'db-password' = $values['DB_PASSWORD']
    'jwt-secret' = $values['JWT_SECRET']
}
$notificationValues = @('ACS_CONNECTION_STRING', 'ACS_SENDER_ADDRESS', 'NOTIFICATION_RECIPIENTS')
if ($IncludeNotifications) {
    foreach ($key in $notificationValues) {
        if ([string]::IsNullOrWhiteSpace($values[$key])) { throw "Missing $key for notification setup." }
    }
    $secrets['acs-connection-string'] = $values['ACS_CONNECTION_STRING']
    $secrets['acs-sender-address'] = $values['ACS_SENDER_ADDRESS']
    $secrets['notification-recipients'] = $values['NOTIFICATION_RECIPIENTS']
}
if (-not $Apply) {
    Write-Host "Validated private Neon/JWT configuration. Ready to upload $($secrets.Count) named secrets after Azure approval."
    return
}

$subscription = (& az account show --query id -o tsv).Trim()
if ($LASTEXITCODE -ne 0 -or $subscription -ne '90f1336d-e3cc-4aae-87f7-fcd90e2b877c') {
    throw 'Azure CLI is not signed in to the approved subscription.'
}
$vaultId = (& az keyvault show --name $VaultName --query id -o tsv).Trim()
if ($LASTEXITCODE -ne 0 -or $vaultId -notlike "*/resourceGroups/rg-secretshield-dev-zboc5k/providers/Microsoft.KeyVault/vaults/$VaultName") {
    throw 'The expected application Key Vault has not been deployed.'
}
$token = (& az account get-access-token --resource 'https://vault.azure.net' --query accessToken -o tsv).Trim()
if ($LASTEXITCODE -ne 0 -or -not $token) { throw 'Could not obtain a Key Vault access token.' }

Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(20)
$client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $token)
try {
    foreach ($entry in $secrets.GetEnumerator()) {
        $uri = [Uri]("https://$VaultName.vault.azure.net/secrets/$($entry.Key)?api-version=7.4")
        $json = @{ value = $entry.Value } | ConvertTo-Json -Compress
        $content = New-Object System.Net.Http.StringContent($json, [Text.Encoding]::UTF8, 'application/json')
        try {
            $response = $client.PutAsync($uri, $content).GetAwaiter().GetResult()
            try {
                if (-not $response.IsSuccessStatusCode) {
                    throw "Key Vault rejected $($entry.Key) with HTTP $([int]$response.StatusCode)."
                }
                Write-Host "Stored Key Vault secret $($entry.Key)."
            } finally { $response.Dispose() }
        } finally { $content.Dispose() }
    }
} finally {
    $client.Dispose()
    $token = $null
}
