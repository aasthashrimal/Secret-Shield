[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9a-f]{40}$')]
    [string]$CommitSha,
    [string]$ApiBase = 'https://secretshield-dev-zboc5k-api.azurewebsites.net',
    [ValidateRange(1, [long]::MaxValue)]
    [long]$RepositoryId = 4,
    [switch]$SendApprovedSyntheticEmail
)

$ErrorActionPreference = 'Stop'
if (-not $SendApprovedSyntheticEmail) {
    throw 'Use -SendApprovedSyntheticEmail only after approving one live ACS test message.'
}
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

$token = $null
$machineCredential = $null
$credentialId = $null
$credentialRevoked = $false
try {
    $loginBody = @{ email = $email; password = $password } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Method Post -Uri "$ApiBase/api/auth/login" `
        -ContentType 'application/json' -Body $loginBody -TimeoutSec 30
    $token = $login.token
    if ([string]::IsNullOrWhiteSpace($token)) { throw 'Login returned no token.' }
    $headers = @{ Authorization = "Bearer $token" }
    Write-Host 'Authenticated. Creating a short-lived verification credential.'

    $expiry = [DateTimeOffset]::UtcNow.AddMinutes(15).ToString('o')
    $issued = Invoke-RestMethod -Method Post `
        -Uri "$ApiBase/api/repositories/$RepositoryId/ci-credentials" `
        -Headers $headers -ContentType 'application/json' `
        -Body (@{ expiresAt = $expiry } | ConvertTo-Json -Compress) -TimeoutSec 30
    $credentialId = $issued.id
    $machineCredential = $issued.credential
    if ([string]::IsNullOrWhiteSpace($machineCredential) -or -not $credentialId) {
        throw 'Backend returned no temporary machine credential.'
    }

    $fixtureId = [Guid]::NewGuid().ToString('N')
    $payload = @{
        runId         = "m15-acs-$fixtureId"
        runAttempt    = 1
        commitSha     = $CommitSha
        branch        = 'main'
        trigger       = 'CI_PUSH'
        scannerVersion = '8.30.0'
        status        = 'FINDINGS_DETECTED'
        failureCode   = $null
        findings      = @(@{
            ruleId              = 'generic-api-key'
            filePath            = "verification/m15-acs-$fixtureId.txt"
            line                = 1
            sanitizedDescription = 'Synthetic high-risk notification verification'
        })
    } | ConvertTo-Json -Depth 6 -Compress

    $scan = Invoke-RestMethod -Method Post `
        -Uri "$ApiBase/api/ci/repositories/$RepositoryId/scan-results" `
        -Headers @{ 'X-Secret-Shield-Credential' = $machineCredential } `
        -ContentType 'application/json' -Body $payload -TimeoutSec 30
    Write-Host 'Synthetic finding ingested. Waiting for the ACS delivery status.'
} finally {
    $password = $null
    $passwordSecure = $null
    $machineCredential = $null
    if ($credentialId -and $token) {
        try {
            Invoke-RestMethod -Method Post `
                -Uri "$ApiBase/api/repositories/$RepositoryId/ci-credentials/$credentialId/revoke" `
                -Headers @{ Authorization = "Bearer $token" } -TimeoutSec 30 | Out-Null
            $credentialRevoked = $true
        } catch {
            Write-Warning 'Temporary credential revocation could not be confirmed; revoke it from the backend.'
        }
    }
}

$headers = @{ Authorization = "Bearer $token" }
$findingPage = Invoke-RestMethod `
    -Uri "$ApiBase/api/repositories/$RepositoryId/scans/$($scan.id)/findings" `
    -Headers $headers -TimeoutSec 30
$finding = @($findingPage.content) | Select-Object -First 1
if (-not $finding -or -not $finding.incidentId) {
    throw 'The synthetic finding or incident was not persisted.'
}

$incident = Invoke-RestMethod `
    -Uri "$ApiBase/api/repositories/$RepositoryId/incidents/$($finding.incidentId)" `
    -Headers $headers -TimeoutSec 30
$delivery = $null
for ($poll = 0; $poll -lt 18; $poll++) {
    $page = Invoke-RestMethod `
        -Uri "$ApiBase/api/repositories/$RepositoryId/incidents/$($incident.id)/notifications" `
        -Headers $headers -TimeoutSec 30
    $delivery = @($page.content) | Select-Object -First 1
    if ($delivery -and ($delivery.status -eq 'SENT' -or
            ($delivery.status -eq 'FAILED' -and $delivery.attemptCount -ge 3))) {
        break
    }
    if ($delivery) {
        Write-Host "Delivery status: $($delivery.status); attempt $($delivery.attemptCount)."
    } else {
        Write-Host 'Delivery is queued.'
    }
    Start-Sleep -Seconds 5
}

$auditPage = Invoke-RestMethod `
    -Uri "$ApiBase/api/repositories/$RepositoryId/audit-events?page=0&size=50&sort=occurredAt,desc" `
    -Headers $headers -TimeoutSec 30
$audit = @($auditPage.content) | Where-Object {
    $_.targetType -eq 'INCIDENT' -and $_.targetId -eq $incident.id
} | Select-Object -First 1

$result = [pscustomobject]@{
    ScanId              = $scan.id
    ScanStatus          = $scan.status
    FindingSeverity     = $finding.severity
    IncidentId          = $incident.id
    IncidentStatus      = $incident.status
    AuditOperation      = $audit.operation
    NotificationStatus  = $delivery.status
    NotificationAttempts = $delivery.attemptCount
    SafeErrorCode       = $delivery.safeErrorCode
    CredentialRevoked   = $credentialRevoked
}
$result

$token = $null
if (-not $credentialRevoked) { throw 'Temporary credential revocation was not verified.' }
if (-not $delivery -or $delivery.status -ne 'SENT') {
    throw 'The live ACS notification did not reach SENT state.'
}
