param(
    [string]$Workspace = (Join-Path $PSScriptRoot '..\target\scanner-workspace')
)

$ErrorActionPreference = 'Stop'
$workspacePath = [IO.Path]::GetFullPath($Workspace)
$targetRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\target'))
if (-not $workspacePath.StartsWith($targetRoot + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Fixture workspace must be inside this project target directory.'
}

$ownerPath = Join-Path $workspacePath 'fixture-owner'
if (Test-Path -LiteralPath $ownerPath) {
    Remove-Item -LiteralPath $ownerPath -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $ownerPath | Out-Null

function New-FixtureRepository {
    param([string]$Name, [bool]$WithFinding)
    $repositoryPath = Join-Path $ownerPath $Name
    New-Item -ItemType Directory -Path $repositoryPath | Out-Null
    & git -C $repositoryPath init -b main --quiet
    & git -C $repositoryPath config user.name 'Secret Shield Fixture'
    & git -C $repositoryPath config user.email 'fixture@invalid.example'
    Set-Content -LiteralPath (Join-Path $repositoryPath 'README.md') -Value "# $Name" -Encoding UTF8
    & git -C $repositoryPath add README.md
    & git -C $repositoryPath commit --quiet -m 'Create clean fixture'
    if ($WithFinding) {
        # Build the marker in generated, ignored history; never store a credential-shaped value in source control.
        $marker = ('SECRET_SHIELD_' + 'SYNTHETIC_' + ('A' * 24))
        Set-Content -LiteralPath (Join-Path $repositoryPath 'fixture.txt') -Value "verification_marker=$marker" -Encoding UTF8
        & git -C $repositoryPath add fixture.txt
        & git -C $repositoryPath commit --quiet -m 'Add synthetic scanner marker'
    }
    if ($LASTEXITCODE -ne 0) { throw "Could not create fixture repository $Name." }
    $commit = (& git -C $repositoryPath rev-parse HEAD).Trim()
    Write-Host "$Name commit: $commit"
}

New-FixtureRepository -Name 'clean-fixture' -WithFinding $false
New-FixtureRepository -Name 'finding-fixture' -WithFinding $true
Write-Host "Fixtures created under $workspacePath (ignored by Git)."
