[CmdletBinding()]
param(
    [string]$ApiBase = 'https://secretshield-dev-zboc5k-api.azurewebsites.net',
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
$repository = 'aasthashrimal/Secret-Shield'
if ($ApiBase -ne 'https://secretshield-dev-zboc5k-api.azurewebsites.net') {
    throw 'CI reporting must target the reviewed M14 backend URL.'
}
if (-not $Apply) {
    Write-Host 'Planned CI setup only: sign in, find/create the Secret-Shield repository record, issue one machine credential, and save it as a GitHub Actions secret.'
    Write-Host 'No backend or GitHub request was sent.'
    return
}
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw 'Install and authenticate GitHub CLI before setting up CI reporting.'
}
$actualRepository = (& gh repo view $repository --json nameWithOwner --jq .nameWithOwner).Trim()
if ($LASTEXITCODE -ne 0 -or $actualRepository -cne $repository) {
    throw 'GitHub CLI is not authenticated to the expected repository.'
}

$email = Read-Host 'Secret Shield account email'
$securePassword = Read-Host 'Secret Shield account password' -AsSecureString
$password = [Net.NetworkCredential]::new('', $securePassword).Password
try {
    $loginBody = @{ email = $email; password = $password } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Method Post -Uri "$ApiBase/api/auth/login" -ContentType 'application/json' -Body $loginBody
} catch { throw 'Secret Shield login failed; check that the approved cloud backend is ready.' }
finally { $password = $null; $loginBody = $null; $securePassword = $null }
if ([string]::IsNullOrWhiteSpace($login.token)) { throw 'Login did not return a token.' }
$headers = @{ Authorization = "Bearer $($login.token)" }

$page = Invoke-RestMethod -Method Get -Uri "$ApiBase/api/repositories?size=100" -Headers $headers
$matching = @($page.content | Where-Object { $_.githubOwner -eq 'aasthashrimal' -and $_.githubName -eq 'Secret-Shield' })
if ($matching.Count -gt 1) { throw 'Multiple matching backend repository records need review.' }
if ($matching.Count -eq 1) {
    $record = $matching[0]
} else {
    $record = Invoke-RestMethod -Method Post -Uri "$ApiBase/api/repositories" -Headers $headers -ContentType 'application/json' `
        -Body (@{ githubOwner = 'aasthashrimal'; githubName = 'Secret-Shield'; defaultBranch = 'main' } | ConvertTo-Json -Compress)
}
if ([string]$record.id -notmatch '^[1-9][0-9]*$') { throw 'Backend returned an invalid repository ID.' }

$issued = $null
try {
    $issued = Invoke-RestMethod -Method Post -Uri "$ApiBase/api/repositories/$($record.id)/ci-credentials" -Headers $headers -ContentType 'application/json' -Body '{}'
    if ([string]::IsNullOrWhiteSpace($issued.credential) -or -not $issued.id) {
        throw 'Backend did not issue a usable CI credential.'
    }
    $issued.credential | & gh secret set SECRET_SHIELD_CI_CREDENTIAL --repo $repository
    if ($LASTEXITCODE -ne 0) { throw 'GitHub rejected the machine credential.' }
    & gh variable set SECRET_SHIELD_REPOSITORY_ID --repo $repository --body ([string]$record.id)
    if ($LASTEXITCODE -ne 0) { throw 'Could not set the repository ID variable.' }
    & gh variable set SECRET_SHIELD_API_BASE --repo $repository --body $ApiBase
    if ($LASTEXITCODE -ne 0) { throw 'Could not set the backend URL variable.' }
    Write-Host 'CI reporting is configured. The one-time machine credential was stored only in GitHub Actions secrets.'
} catch {
    if ($issued -and $issued.id) {
        try {
            Invoke-RestMethod -Method Post -Uri "$ApiBase/api/repositories/$($record.id)/ci-credentials/$($issued.id)/revoke" -Headers $headers | Out-Null
        } catch { Write-Warning 'Credential setup failed and automatic revocation could not be confirmed; revoke that issued credential in the backend.' }
    }
    throw 'CI reporting setup failed; any issued credential was revoked where possible.'
} finally {
    if ($issued) { $issued.credential = $null }
    $login.token = $null
}
