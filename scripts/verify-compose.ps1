$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$composeEnv = Join-Path $root '.env.compose'
if (-not (Test-Path -LiteralPath $composeEnv)) {
    throw 'Run scripts/init-compose-env.ps1 first.'
}

function Assert-Result([bool]$condition, [string]$message) {
    if (-not $condition) { throw $message }
    Write-Host "PASS $message"
}

function Post-Json([string]$uri, [object]$body, [hashtable]$headers = @{}) {
    Invoke-RestMethod -Method Post -Uri $uri -Headers $headers -ContentType 'application/json' `
        -Body ($body | ConvertTo-Json -Depth 5 -Compress)
}

function Invoke-Compose([string[]]$composeArgs) {
    & docker compose --env-file $composeEnv @composeArgs
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose command failed.' }
}

$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $bytes = New-Object byte[] 24
    $rng.GetBytes($bytes)
    $password = [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 10)
    $email = "m13-$suffix@example.invalid"
    $base = 'http://localhost:5173'

    Push-Location $root
    try {
        $health = Invoke-RestMethod -Uri "$base/api/health/live"
        Assert-Result ($health.status -eq 'UP') 'Frontend proxy reaches the backend'

        $null = Post-Json "$base/api/auth/register" `
            @{ username = "m13-$suffix"; email = $email; password = $password }
        $login = Post-Json "$base/api/auth/login" @{ email = $email; password = $password }
        $headers = @{ Authorization = "Bearer $($login.token)" }
        $me = Invoke-RestMethod -Uri "$base/api/auth/me" -Headers $headers
        Assert-Result ($me.email -eq $email) 'Register, login, and authenticated API work'

        foreach ($fixture in @(@{ name = 'clean-fixture'; expected = 'CLEAN' },
                             @{ name = 'finding-fixture'; expected = 'FINDINGS_DETECTED' })) {
            $source = Join-Path $root "target/scanner-workspace/fixture-owner/$($fixture.name)"
            if (-not (Test-Path -LiteralPath (Join-Path $source '.git'))) {
                throw 'Run scripts/prepare-scanner-fixtures.ps1 before verification.'
            }
            $sha = (& git -C $source rev-parse HEAD).Trim()
            if ($LASTEXITCODE -ne 0) { throw 'Could not read fixture commit.' }
            $repository = Post-Json "$base/api/repositories" `
                @{ githubOwner = 'fixture-owner'; githubName = $fixture.name; defaultBranch = 'main' } $headers
            $scan = Post-Json "$base/api/repositories/$($repository.id)/scans" `
                @{ branch = 'main'; commitSha = $sha } $headers
            $result = Invoke-RestMethod -Method Post `
                -Uri "$base/api/repositories/$($repository.id)/scans/$($scan.id)/start" -Headers $headers
            Assert-Result ($result.status -eq $fixture.expected -and $result.scannerVersion -eq '8.30.0') `
                "$($fixture.name) scan is $($fixture.expected) with Gitleaks 8.30.0"
        }

        Invoke-Compose @('down')
        Invoke-Compose @('up', '-d', '--pull', 'never', '--wait', '--wait-timeout', '180')
        $relogin = Post-Json "$base/api/auth/login" @{ email = $email; password = $password }
        $page = Invoke-RestMethod -Uri "$base/api/repositories?size=100" `
            -Headers @{ Authorization = "Bearer $($relogin.token)" }
        $names = @($page.content | ForEach-Object { $_.githubName })
        Assert-Result ($names -contains 'clean-fixture' -and $names -contains 'finding-fixture') `
            'Account and repository data survive Compose down/up'
    } finally {
        Pop-Location
    }
} finally {
    $rng.Dispose()
}
