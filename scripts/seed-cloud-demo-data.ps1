[CmdletBinding()]
param(
    [string]$ApiBase = 'https://secretshield-dev-zboc5k-web.azurewebsites.net',
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'

function Get-DemoSha([string]$Value) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
        return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant().Substring(0, 40)
    } finally {
        $sha.Dispose()
    }
}

function New-Finding([string]$Rule, [string]$Path, [int]$Line) {
    return @{
        ruleId              = $Rule
        filePath            = $Path
        line                = $Line
        sanitizedDescription = "Synthetic presentation metadata for $Rule"
    }
}

function New-Scan([string]$RunId, [string]$Status, [array]$Findings, [string]$FailureCode = $null) {
    return @{
        runId         = $RunId
        runAttempt    = 1
        commitSha     = Get-DemoSha $RunId
        branch        = 'main'
        trigger       = 'CI_PUSH'
        scannerVersion = '8.30.0'
        status        = $Status
        failureCode   = $FailureCode
        findings      = @($Findings)
    }
}

$plans = @(
    @{
        name = 'payments-api'
        scans = @(
            (New-Scan 'demo-seed.payments.clean' 'CLEAN' @()),
            (New-Scan 'demo-seed.payments.findings' 'FINDINGS_DETECTED' @(
                (New-Finding 'generic-password' 'config/application-demo.yml' 14),
                (New-Finding 'synthetic-low' 'docs/example-config.md' 8)
            ))
        )
        resolveOne = $true
        deactivate = $false
    },
    @{
        name = 'customer-portal'
        scans = @(
            (New-Scan 'demo-seed.portal.findings' 'FINDINGS_DETECTED' @(
                (New-Finding 'generic-password' 'src/config/demo-settings.ts' 22),
                (New-Finding 'synthetic-low' 'config/demo-feature.json' 7),
                (New-Finding 'synthetic-info' 'docs/demo-notes.md' 4)
            )),
            (New-Scan 'demo-seed.portal.clean' 'CLEAN' @())
        )
        resolveOne = $true
        deactivate = $false
    },
    @{
        name = 'infrastructure-config'
        scans = @(
            (New-Scan 'demo-seed.infrastructure.failed' 'FAILED' @() 'DEMO_SCANNER_TIMEOUT'),
            (New-Scan 'demo-seed.infrastructure.clean' 'CLEAN' @())
        )
        resolveOne = $false
        deactivate = $false
    },
    @{
        name = 'legacy-worker'
        scans = @(
            (New-Scan 'demo-seed.legacy.clean' 'CLEAN' @())
        )
        resolveOne = $false
        deactivate = $true
    }
)

$findingCount = ($plans | ForEach-Object { $_.scans } | ForEach-Object { $_.findings }).Count
[pscustomobject]@{
    Mode                  = if ($Apply) { 'APPLY' } else { 'PREVIEW' }
    ApiBase               = $ApiBase.TrimEnd('/')
    SyntheticRepositories = $plans.Count
    SyntheticScans        = ($plans | ForEach-Object { $_.scans }).Count
    SyntheticFindings     = $findingCount
    HighRiskFindings      = 0
    EmailsQueued          = 0
}

$plans | ForEach-Object {
    [pscustomobject]@{
        Repository = "secret-shield-demo/$($_.name)"
        Scans      = $_.scans.Count
        Findings   = ($_.scans | ForEach-Object { $_.findings }).Count
        FinalState = if ($_.deactivate) { 'INACTIVE' } else { 'ACTIVE' }
    }
} | Format-Table -AutoSize

if (-not $Apply) {
    Write-Host 'Preview only. No HTTP request was sent and no cloud data changed.'
    Write-Host 'Use -Apply only after reviewing this exact synthetic dataset.'
    exit 0
}

$base = $ApiBase.TrimEnd('/')
$email = Read-Host 'Synthetic presentation account email'
$passwordSecure = Read-Host 'Synthetic presentation account password' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($passwordSecure)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}

try {
    $loginBody = @{ email = $email; password = $password } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" `
        -ContentType 'application/json' -Body $loginBody
} finally {
    $password = $null
    $passwordSecure = $null
    $loginBody = $null
}

$headers = @{ Authorization = "Bearer $($login.token)" }
$repositories = Invoke-RestMethod -Uri "$base/api/repositories?size=100&sort=createdAt,desc" -Headers $headers
$createdRepositories = 0
$createdScans = 0
$skippedScans = 0
$resolvedIncidents = 0

foreach ($plan in $plans) {
    $repository = $repositories.content | Where-Object {
        $_.githubOwner -eq 'secret-shield-demo' -and $_.githubName -eq $plan.name
    } | Select-Object -First 1

    if (-not $repository) {
        $repositoryBody = @{
            githubOwner  = 'secret-shield-demo'
            githubName   = $plan.name
            defaultBranch = 'main'
        } | ConvertTo-Json -Compress
        $repository = Invoke-RestMethod -Method Post -Uri "$base/api/repositories" `
            -Headers $headers -ContentType 'application/json' -Body $repositoryBody
        $createdRepositories++
    }

    $existingScans = Invoke-RestMethod `
        -Uri "$base/api/repositories/$($repository.id)/scans?size=100&sort=createdAt,desc" `
        -Headers $headers
    $pendingScans = @($plan.scans | Where-Object { $run = $_.runId; -not ($existingScans.content | Where-Object { $_.runId -eq $run }) })

    if ($pendingScans.Count -gt 0) {
        if (-not $repository.active) {
            throw "Synthetic repository $($plan.name) is inactive but is missing planned scans."
        }
        $issued = Invoke-RestMethod -Method Post `
            -Uri "$base/api/repositories/$($repository.id)/ci-credentials" `
            -Headers $headers -ContentType 'application/json' -Body '{}'
        try {
            $machineHeaders = @{ 'X-Secret-Shield-Credential' = $issued.credential }
            foreach ($scan in $pendingScans) {
                $scanBody = $scan | ConvertTo-Json -Depth 6 -Compress
                $null = Invoke-RestMethod -Method Post `
                    -Uri "$base/api/ci/repositories/$($repository.id)/scan-results" `
                    -Headers $machineHeaders -ContentType 'application/json' -Body $scanBody
                $createdScans++
            }
        } finally {
            $machineHeaders = $null
            if ($issued) {
                Invoke-RestMethod -Method Post `
                    -Uri "$base/api/repositories/$($repository.id)/ci-credentials/$($issued.id)/revoke" `
                    -Headers $headers | Out-Null
                $issued = $null
            }
        }
    }
    $skippedScans += $plan.scans.Count - $pendingScans.Count

    if ($plan.resolveOne) {
        $incidentPage = Invoke-RestMethod `
            -Uri "$base/api/repositories/$($repository.id)/incidents?size=100&sort=createdAt,desc" `
            -Headers $headers
        $incident = $incidentPage.content | Where-Object {
            $_.status -eq 'OPEN' -and ($_.severity -eq 'LOW' -or $_.severity -eq 'INFO')
        } | Select-Object -First 1
        if ($incident) {
            $transition = @{
                status = 'RESOLVED'
                reason = 'Synthetic presentation finding reviewed and remediated.'
                version = $incident.version
            } | ConvertTo-Json -Compress
            $null = Invoke-RestMethod -Method Patch `
                -Uri "$base/api/repositories/$($repository.id)/incidents/$($incident.id)" `
                -Headers $headers -ContentType 'application/json' -Body $transition
            $resolvedIncidents++
        }
    }

    if ($plan.deactivate -and $repository.active) {
        Invoke-RestMethod -Method Post -Uri "$base/api/repositories/$($repository.id)/deactivate" `
            -Headers $headers | Out-Null
    }
}

$overview = Invoke-RestMethod -Uri "$base/api/dashboard/overview" -Headers $headers
$headers = $null
$login = $null

[pscustomobject]@{
    CreatedRepositories = $createdRepositories
    CreatedScans        = $createdScans
    ExistingScans       = $skippedScans
    ResolvedIncidents   = $resolvedIncidents
    DashboardRepositories = $overview.repositories
    DashboardScans      = $overview.scans
    DashboardFindings   = $overview.findings
    DashboardIncidents  = $overview.incidents
    EmailsQueued        = 0
}
