[CmdletBinding()]
param(
    [switch]$Apply,
    [switch]$EnablePublishing,
    [switch]$EnableDeployment,
    [switch]$EnableBootstrapPublishing,
    [switch]$DisableBootstrapPublishing
)
$ErrorActionPreference = 'Stop'
$repository = 'aasthashrimal/Secret-Shield'
$environment = 'azure-dev'
if ($EnableDeployment -and -not $EnablePublishing) {
    throw 'EnablePublishing is required before EnableDeployment.'
}
if ($EnableBootstrapPublishing -and $DisableBootstrapPublishing) {
    throw 'Choose either EnableBootstrapPublishing or DisableBootstrapPublishing.'
}

if (-not $Apply) {
    Write-Host 'Planned GitHub setup only: create azure-dev with a main-only branch rule; add non-secret Azure identity variables from Terraform outputs.'
    if ($EnablePublishing -or $EnableDeployment -or $EnableBootstrapPublishing -or $DisableBootstrapPublishing) {
        Write-Host 'Requested release switches are shown for review only; no GitHub setting was changed.'
    } else {
        Write-Host 'GHCR_PUBLISH_ENABLED, AZURE_DEPLOY_ENABLED, and BOOTSTRAP_PUBLISH_ENABLED remain unset/disabled.'
    }
    return
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw 'Install and authenticate GitHub CLI before applying GitHub environment setup.'
}
$actualRepository = (& gh repo view $repository --json nameWithOwner --jq .nameWithOwner).Trim()
if ($LASTEXITCODE -ne 0 -or $actualRepository -cne $repository) {
    throw 'GitHub CLI is not authenticated to the expected repository.'
}
$root = Split-Path $PSScriptRoot -Parent
Push-Location (Join-Path $root 'infra')
try {
    $clientId = (& terraform output -raw github_deploy_client_id).Trim()
    $tenantId = (& terraform output -raw azure_tenant_id).Trim()
    if ($LASTEXITCODE -ne 0 -or $clientId -notmatch '^[0-9a-fA-F-]{36}$' -or
            $tenantId -notmatch '^[0-9a-fA-F-]{36}$') {
        throw 'M14 application infrastructure must be applied before GitHub setup.'
    }
} finally { Pop-Location }

$apiPath = "repos/$repository/environments/$environment"
$existing = & gh api $apiPath 2>$null
if ($LASTEXITCODE -ne 0) {
    $body = '{"deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}'
    $tempFile = Join-Path $root 'target/github-environment-setup.json'
    [IO.File]::WriteAllText($tempFile, $body, (New-Object Text.UTF8Encoding($false)))
    try {
        & gh api -X PUT $apiPath --input $tempFile | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the protected GitHub environment.' }
    } finally { Remove-Item -LiteralPath $tempFile -Force }
} else {
    $policy = ($existing | ConvertFrom-Json).deployment_branch_policy
    if (-not $policy.custom_branch_policies) {
        throw 'The existing azure-dev environment has a different branch policy; review it before changing it.'
    }
}

$branchPath = "$apiPath/deployment-branch-policies"
$branches = & gh api $branchPath | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect deployment branch policies.' }
if (-not @($branches.branch_policies | Where-Object { $_.name -eq 'main' -and $_.type -eq 'branch' }).Count) {
    & gh api -X POST $branchPath -f name=main -f type=branch | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not restrict deployment to main.' }
}
& gh variable set AZURE_CLIENT_ID --repo $repository --env $environment --body $clientId
if ($LASTEXITCODE -ne 0) { throw 'Could not set the Azure client ID.' }
& gh variable set AZURE_TENANT_ID --repo $repository --env $environment --body $tenantId
if ($LASTEXITCODE -ne 0) { throw 'Could not set the Azure tenant ID.' }
& gh variable set AZURE_SUBSCRIPTION_ID --repo $repository --env $environment --body '90f1336d-e3cc-4aae-87f7-fcd90e2b877c'
if ($LASTEXITCODE -ne 0) { throw 'Could not set the Azure subscription ID.' }
if ($EnablePublishing) {
    & gh variable set GHCR_PUBLISH_ENABLED --repo $repository --body true
    if ($LASTEXITCODE -ne 0) { throw 'Could not enable GHCR publishing.' }
}
if ($EnableDeployment) {
    & gh variable set AZURE_DEPLOY_ENABLED --repo $repository --body true
    if ($LASTEXITCODE -ne 0) { throw 'Could not enable Azure deployment.' }
}
if ($EnableBootstrapPublishing -or $DisableBootstrapPublishing) {
    $bootstrapValue = if ($EnableBootstrapPublishing) { 'true' } else { 'false' }
    & gh variable set BOOTSTRAP_PUBLISH_ENABLED --repo $repository --body $bootstrapValue
    if ($LASTEXITCODE -ne 0) { throw 'Could not update the one-time bootstrap publishing switch.' }
}
Write-Host 'GitHub azure-dev environment and non-secret identity variables prepared.'
if (-not $EnablePublishing) { Write-Host 'Publishing and deployment switches remain unchanged.' }
