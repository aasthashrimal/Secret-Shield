[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$CommitSha,
    [Parameter(Mandatory)][ValidateRange(1, [long]::MaxValue)][long]$RunId,
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
$repository = 'aasthashrimal/Secret-Shield'
$resourceGroup = 'rg-secretshield-dev-zboc5k'
$backendName = 'secretshield-dev-zboc5k-api'
$frontendName = 'secretshield-dev-zboc5k-web'
$backendImage = "ghcr.io/aasthashrimal/secret-shield-backend:$CommitSha"
$frontendImage = "ghcr.io/aasthashrimal/secret-shield-frontend:$CommitSha"

Write-Host "Bootstrap candidate: GitHub run $RunId, main commit $CommitSha."
Write-Host "Images: $backendImage and $frontendImage."
if (-not $Apply) {
    Write-Host 'Dry run only. No Azure app setting was changed.'
    return
}

foreach ($command in @('gh', 'az', 'docker')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required command $command is unavailable."
    }
}
$runText = & gh run view $RunId --repo $repository --json 'headSha,headBranch,event,conclusion,jobs'
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the requested GitHub bootstrap run.' }
$run = $runText | ConvertFrom-Json
if ($run.headSha -cne $CommitSha -or $run.headBranch -cne 'main' -or
        $run.event -cne 'workflow_dispatch' -or $run.conclusion -cne 'success') {
    throw 'The bootstrap run is not a successful workflow_dispatch for the requested main commit.'
}
foreach ($jobName in @('Secret scan and gate', 'Backend and frontend checks', 'One-time bootstrap image publish')) {
    $jobs = @($run.jobs | Where-Object { $_.name -ceq $jobName -and $_.conclusion -ceq 'success' })
    if ($jobs.Count -ne 1) { throw "The bootstrap run lacks successful job $jobName." }
}

$anonymousConfig = Join-Path (Split-Path $PSScriptRoot -Parent) 'target/bootstrap-anonymous-docker-config'
New-Item -ItemType Directory -Force -Path $anonymousConfig | Out-Null
foreach ($image in @($backendImage, $frontendImage)) {
    & docker --config $anonymousConfig manifest inspect $image *> $null
    if ($LASTEXITCODE -ne 0) { throw "Anonymous manifest inspection failed for $image. Make the GHCR package public first." }
}
$subscription = (& az account show --query id -o tsv).Trim()
if ($LASTEXITCODE -ne 0 -or $subscription -ne '90f1336d-e3cc-4aae-87f7-fcd90e2b877c') {
    throw 'Azure CLI is not signed in to the approved subscription.'
}
foreach ($app in @($backendName, $frontendName)) {
    $state = (& az webapp show --resource-group $resourceGroup --name $app --query state -o tsv).Trim()
    if ($LASTEXITCODE -ne 0 -or $state -ne 'Running') { throw "App $app is not running." }
}

& az webapp config container set --resource-group $resourceGroup --name $backendName `
    --container-image-name $backendImage --container-registry-url 'https://ghcr.io' --output none
if ($LASTEXITCODE -ne 0) { throw 'Backend image update failed; inspect Azure app state before retrying.' }
& az webapp config container set --resource-group $resourceGroup --name $frontendName `
    --container-image-name $frontendImage --container-registry-url 'https://ghcr.io' --output none
if ($LASTEXITCODE -ne 0) { throw 'Frontend image update failed; backend may already be updated.' }

foreach ($uri in @(
        'https://secretshield-dev-zboc5k-api.azurewebsites.net/api/health/live',
        'https://secretshield-dev-zboc5k-web.azurewebsites.net/health',
        'https://secretshield-dev-zboc5k-web.azurewebsites.net/api/health/live'
    )) {
    $healthy = $false
    for ($attempt = 1; $attempt -le 18; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 10
            if ($response.StatusCode -eq 200) {
                if ($uri.EndsWith('/health')) {
                    $healthy = $response.Content.Trim() -eq 'UP'
                } else {
                    $payload = $response.Content | ConvertFrom-Json
                    $healthy = $payload.status -eq 'UP'
                }
                if ($healthy) { break }
            }
        } catch { }
        if ($attempt -lt 18) { Start-Sleep -Seconds 10 }
    }
    if (-not $healthy) { throw "Health check did not pass for $uri. The image settings may have changed; inspect logs and rollback." }
    Write-Host "Healthy: $uri"
}
Write-Host "Bootstrap deployment completed for exact commit $CommitSha. Configure trusted backend CI reporting before enabling normal publish/deploy."
