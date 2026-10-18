[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$repository = 'aasthashrimal/Secret-Shield'
$path = "repos/$repository/branches/main/protection"
$checks = @('Secret scan and gate', 'Backend and frontend checks')

if (-not $Apply) {
    Write-Host "Planned GitHub change for $repository main: require up-to-date checks '$($checks -join "' and '")', enforce for administrators, and disallow force pushes/deletion. Review approvals are not required for solo work. No settings changed."
    return
}
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw 'Install and authenticate GitHub CLI before changing branch protection.'
}
$actualRepository = (& gh repo view $repository --json nameWithOwner --jq .nameWithOwner).Trim()
if ($LASTEXITCODE -ne 0 -or $actualRepository -cne $repository) {
    throw 'GitHub CLI is not authenticated to the expected repository.'
}
$existing = $null
$lookupError = $null
try { $existing = & gh api $path 2>&1 }
catch { $lookupError = $_.Exception.Message }
if ($LASTEXITCODE -eq 0) {
    throw 'main already has branch protection. Review its current settings before changing it.'
}
if ($lookupError -notmatch 'HTTP 404') {
    throw 'Could not determine whether main already has branch protection.'
}
$checkNames = & gh api "repos/$repository/commits/main/check-runs" --jq '.check_runs[].name'
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect existing check runs.' }
foreach ($check in $checks) {
    if ($checkNames -cnotcontains $check) {
        throw "Required check '$check' has not run on main yet. Review GitHub before applying."
    }
}
$settings = @{
    required_status_checks = @{ strict = $true; contexts = $checks }
    enforce_admins = $true
    required_pull_request_reviews = $null
    restrictions = $null
    required_linear_history = $false
    allow_force_pushes = $false
    allow_deletions = $false
    required_conversation_resolution = $true
} | ConvertTo-Json -Depth 6
$root = Split-Path $PSScriptRoot -Parent
$tempFile = Join-Path $root 'target/github-branch-protection.json'
[IO.File]::WriteAllText($tempFile, $settings, (New-Object Text.UTF8Encoding($false)))
try {
    & gh api -X PUT $path --input $tempFile | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'GitHub rejected branch protection setup.' }
} finally { Remove-Item -LiteralPath $tempFile -Force }
$result = & gh api $path | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or -not $result.enforce_admins.enabled -or
        -not $result.required_status_checks.strict -or
        @($result.required_status_checks.contexts).Count -ne $checks.Count -or
        @($result.required_status_checks.contexts | Where-Object { $checks -cnotcontains $_ }).Count -ne 0 -or
        $result.allow_force_pushes.enabled -or $result.allow_deletions.enabled) {
    throw 'Branch protection changed, but verification did not match the requested settings.'
}
Write-Host 'main branch protection verified with both required checks.'
