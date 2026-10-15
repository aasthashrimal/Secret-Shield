param(
    [string]$Destination = (Join-Path $PSScriptRoot '..\target\tools\gitleaks\8.30.0')
)

$ErrorActionPreference = 'Stop'
$version = '8.30.0'
$expectedSha256 = '54fe94f644b832dd08e8c3a5915efb3bfa862386d59fb27ca0792cb687a83573'
$destinationPath = [IO.Path]::GetFullPath($Destination)
$archive = Join-Path ([IO.Path]::GetTempPath()) ("secret-shield-gitleaks-$([guid]::NewGuid()).zip")
$uri = "https://github.com/gitleaks/gitleaks/releases/download/v$version/gitleaks_${version}_windows_x64.zip"

try {
    New-Item -ItemType Directory -Force -Path $destinationPath | Out-Null
    Invoke-WebRequest -Uri $uri -OutFile $archive
    $actualSha256 = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualSha256 -ne $expectedSha256) {
        throw 'Downloaded Gitleaks archive failed the pinned SHA-256 check.'
    }
    Expand-Archive -LiteralPath $archive -DestinationPath $destinationPath -Force
    $executable = Join-Path $destinationPath 'gitleaks.exe'
    $reported = & $executable version 2>&1
    if ($LASTEXITCODE -ne 0 -or "$reported" -notmatch "\b$([regex]::Escape($version))\b") {
        throw 'Installed Gitleaks executable did not report the pinned version.'
    }
    Write-Host "Installed verified Gitleaks $version at $executable"
} finally {
    Remove-Item -LiteralPath $archive -Force -ErrorAction SilentlyContinue
}
