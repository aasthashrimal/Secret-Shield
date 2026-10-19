[CmdletBinding()]
param(
    [string]$ApiBase = 'https://secretshield-dev-zboc5k-api.azurewebsites.net',
    [ValidateRange(1, [long]::MaxValue)]
    [long]$RepositoryId = 4
)

$ErrorActionPreference = 'Stop'
if ($ApiBase -ne 'https://secretshield-dev-zboc5k-api.azurewebsites.net') {
    throw 'Cloud notification verification must target the reviewed backend URL.'
}

$email = (Read-Host 'Secret Shield account email').Trim()
$passwordSecure = Read-Host 'Secret Shield account password' -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($passwordSecure)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}

try {
    $login = Invoke-RestMethod -Method Post -Uri "$ApiBase/api/auth/login" `
        -ContentType 'application/json' -TimeoutSec 30 `
        -Body (@{ email = $email; password = $password } | ConvertTo-Json -Compress)
} finally {
    $password = $null
    $passwordSecure = $null
}
if ([string]::IsNullOrWhiteSpace($login.token)) { throw 'Login returned no token.' }
$headers = @{ Authorization = "Bearer $($login.token)" }

$scans = Invoke-RestMethod `
    -Uri "$ApiBase/api/repositories/$RepositoryId/scans?page=0&size=50&sort=createdAt,desc" `
    -Headers $headers -TimeoutSec 30
$scan = @($scans.content) | Where-Object { $_.runId -like 'm15-acs-*' } | Select-Object -First 1
if (-not $scan) { throw 'No M15 ACS verification scan exists; the interrupted run did not ingest one.' }

$findingPage = Invoke-RestMethod `
    -Uri "$ApiBase/api/repositories/$RepositoryId/scans/$($scan.id)/findings" `
    -Headers $headers -TimeoutSec 30
$finding = @($findingPage.content) | Select-Object -First 1
if (-not $finding -or -not $finding.incidentId) { throw 'The verification finding has no incident.' }

$incident = Invoke-RestMethod `
    -Uri "$ApiBase/api/repositories/$RepositoryId/incidents/$($finding.incidentId)" `
    -Headers $headers -TimeoutSec 30
$notificationPage = Invoke-RestMethod `
    -Uri "$ApiBase/api/repositories/$RepositoryId/incidents/$($incident.id)/notifications" `
    -Headers $headers -TimeoutSec 30
$delivery = @($notificationPage.content) | Select-Object -First 1

$auditPage = Invoke-RestMethod `
    -Uri "$ApiBase/api/repositories/$RepositoryId/audit-events?page=0&size=50&sort=occurredAt,desc" `
    -Headers $headers -TimeoutSec 30
$audit = @($auditPage.content) | Where-Object {
    $_.targetType -eq 'INCIDENT' -and $_.targetId -eq $incident.id
} | Select-Object -First 1

[pscustomobject]@{
    ScanId               = $scan.id
    ScanStatus           = $scan.status
    FindingSeverity      = $finding.severity
    IncidentId           = $incident.id
    IncidentStatus       = $incident.status
    AuditOperation       = $audit.operation
    NotificationStatus   = $delivery.status
    NotificationAttempts = $delivery.attemptCount
    SafeErrorCode         = $delivery.safeErrorCode
}
$login.token = $null
