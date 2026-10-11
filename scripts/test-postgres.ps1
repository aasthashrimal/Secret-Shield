[CmdletBinding()]
param(
    [string]$PostgresBin = $env:PG_BIN,
    [switch]$Offline,
    [string]$CleanupRunId
)

$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$testBase = [IO.Path]::GetFullPath((Join-Path $workspace 'target/postgres-tests'))
foreach ($parent in @((Join-Path $workspace 'target'), $testBase)) {
    if ((Test-Path -LiteralPath $parent) -and
            ((Get-Item -LiteralPath $parent).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'Disposable database directories must not be junctions or symbolic links.'
    }
}
if ([string]::IsNullOrWhiteSpace($PostgresBin)) {
    $psql = Get-Command psql -ErrorAction SilentlyContinue
    if ($psql) { $PostgresBin = Split-Path $psql.Source }
    elseif ($env:ProgramFiles) {
        $installed = @(Get-ChildItem (Join-Path $env:ProgramFiles 'PostgreSQL') -Directory -ErrorAction SilentlyContinue |
            Sort-Object { [version]$_.Name } -Descending)
        foreach ($version in $installed) {
            $candidate = Join-Path $version.FullName 'bin'
            if (Test-Path (Join-Path $candidate 'initdb.exe')) { $PostgresBin = $candidate; break }
        }
    }
}
if ([string]::IsNullOrWhiteSpace($PostgresBin)) {
    throw 'Install PostgreSQL server tools, then supply -PostgresBin or PG_BIN. See docs/testing.md.'
}
$tools = @{}
foreach ($name in @('initdb', 'pg_ctl', 'createdb', 'psql')) {
    $path = Join-Path $PostgresBin ($name + '.exe')
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing PostgreSQL tool: $name" }
    $tools[$name] = $path
}

function Invoke-LoggedTool([string]$Name, [string[]]$Arguments, [string]$Log) {
    # Wait only for this command, not the PostgreSQL server it launches on Windows.
    $quoted = @($Arguments | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' })
    $stdout = $Log + '.stdout'
    $stderr = $Log + '.stderr'
    $process = Start-Process -FilePath $tools[$Name] -ArgumentList $quoted -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $process.Handle | Out-Null
    $process.WaitForExit()
    $result = $process.ExitCode
    foreach ($output in @($stdout, $stderr)) {
        if (Test-Path -LiteralPath $output) { Get-Content -LiteralPath $output | Add-Content -LiteralPath $Log }
    }
    return $result
}

if ($CleanupRunId) {
    if ($CleanupRunId -notmatch '^[a-f0-9]{32}$') { throw 'Invalid disposable run ID.' }
    $cleanupBase = $testBase
    $cleanupTarget = [IO.Path]::GetFullPath((Join-Path $cleanupBase $CleanupRunId))
    if (-not $cleanupTarget.StartsWith($cleanupBase + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath $cleanupTarget)) {
        throw 'Disposable cleanup directory is missing or outside the test workspace.'
    }
    $reparse = @(Get-Item -LiteralPath $cleanupTarget; Get-ChildItem -LiteralPath $cleanupTarget -Recurse -Force)
    if (@($reparse | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) {
        throw 'Refusing cleanup through a reparse point.'
    }
    $cleanupData = Join-Path $cleanupTarget 'data'
    $pidFile = Join-Path $cleanupData 'postmaster.pid'
    if (Test-Path -LiteralPath $pidFile) {
        $pidLines = Get-Content -LiteralPath $pidFile
        if ([IO.Path]::GetFullPath($pidLines[1]) -ne [IO.Path]::GetFullPath($cleanupData)) {
            throw 'PostgreSQL data directory identity mismatch.'
        }
        $serverProcess = Get-Process -Id ([int]$pidLines[0]) -ErrorAction SilentlyContinue
        if ($serverProcess) {
            if ($serverProcess.ProcessName -ne 'postgres') { throw 'Disposable server PID was reused; refusing cleanup.' }
            $code = Invoke-LoggedTool 'pg_ctl' @('-D', $cleanupData, '-m', 'fast', '-w', '-t', '30', 'stop') `
                (Join-Path $cleanupTarget 'cleanup.log')
            if ($code -ne 0) { throw 'Disposable PostgreSQL did not stop; retaining its data.' }
        } elseif (@(Get-NetTCPConnection -State Listen -LocalPort ([int]$pidLines[3]) -ErrorAction SilentlyContinue).Count) {
            throw 'Disposable port is still in use; retaining its data.'
        }
    }
    Remove-Item -LiteralPath $cleanupTarget -Recurse -Force
    Write-Host 'Interrupted disposable run stopped and removed.'
    exit 0
}

$runId = [Guid]::NewGuid().ToString('N')
$testRoot = $testBase
$runRoot = [IO.Path]::GetFullPath((Join-Path $testRoot $runId))
$dataPath = Join-Path $runRoot 'data'
$database = 'secret_shield_test_' + $runId
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start()
$port = $listener.LocalEndpoint.Port
$listener.Stop()
if ($port -eq 5432) { throw 'Refusing default PostgreSQL port; rerun to select another port.' }

# Check the final absolute cleanup target before creating anything.
if (-not $runRoot.StartsWith($testRoot + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase) -or (Split-Path $runRoot -Leaf) -ne $runId) {
    throw 'Unsafe disposable database directory.'
}
if (Test-Path -LiteralPath $runRoot) { throw 'Disposable directory already exists; refusing to reuse it.' }
New-Item -ItemType Directory -Path $runRoot | Out-Null
$bootstrapLog = Join-Path $runRoot 'bootstrap.log'
$serverLog = Join-Path $runRoot 'postgres.log'
$savedEnvironment = @{}
$environmentNames = @('TEST_DB_URL', 'TEST_DB_USERNAME', 'TEST_DB_PASSWORD', 'TEST_DB_RUN_ID',
    'DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET', 'SPRING_DATASOURCE_URL',
    'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD', 'SPRING_PROFILES_ACTIVE',
    'SPRING_PROFILES_DEFAULT', 'SPRING_JPA_HIBERNATE_DDL_AUTO', 'PGPASSWORD', 'PGSERVICE', 'PGSERVICEFILE')
foreach ($name in $environmentNames) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    [Environment]::SetEnvironmentVariable($name, $null, 'Process')
}
$started = $false
$exitCode = 1
$canRemove = $true
function Invoke-PostgresTool([string]$Name, [string[]]$Arguments) {
    $code = Invoke-LoggedTool $Name $Arguments $bootstrapLog
    if ($code -ne 0) { throw "PostgreSQL $Name failed. Inspect the disposable bootstrap log locally." }
}

try {
    Invoke-PostgresTool 'initdb' @('-D', $dataPath, '-U', 'secretshield_test', '-A', 'trust',
        '--encoding=UTF8', '--locale=C', '--no-instructions')
    Invoke-PostgresTool 'pg_ctl' @('-D', $dataPath, '-l', $serverLog,
        '-o', "-h 127.0.0.1 -p $port", '-w', '-t', '30', 'start')
    $started = $true
    Invoke-PostgresTool 'createdb' @('-h', '127.0.0.1', '-p', "$port", '-U', 'secretshield_test', $database)
    # runId is generated hex, not user input. This marker is required before Hibernate writes.
    $sql = "CREATE SCHEMA test_support; CREATE TABLE test_support.test_run_marker (run_id text NOT NULL); INSERT INTO test_support.test_run_marker VALUES ('$runId');"
    Invoke-PostgresTool 'psql' @('-X', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1', '-p', "$port",
        '-U', 'secretshield_test', '-d', $database, '-c', $sql)
    $env:TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$port/$database"
    $env:TEST_DB_USERNAME = 'secretshield_test'
    $env:TEST_DB_PASSWORD = 'synthetic-disposable-test-password'
    $env:TEST_DB_RUN_ID = $runId
    Write-Host 'Running unit and API integration tests against a fresh disposable PostgreSQL cluster.'
    Push-Location $workspace
    try {
        $mavenArgs = @('-Ppostgres-tests', 'test')
        if ($Offline) { $mavenArgs = @('-o') + $mavenArgs }
        & (Join-Path $workspace 'mvnw.cmd') @mavenArgs
        $exitCode = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    # Even a failed startup may leave a server running; stop only this new cluster.
    if ($started -or (Test-Path -LiteralPath (Join-Path $dataPath 'postmaster.pid'))) {
        $code = Invoke-LoggedTool 'pg_ctl' @('-D', $dataPath, '-m', 'fast', '-w', '-t', '30', 'stop') $bootstrapLog
        if ($code -ne 0) {
            $canRemove = $false
            Write-Warning 'Disposable PostgreSQL did not stop. Its data directory was retained; see docs/testing.md.'
        }
    }
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }
    if ($exitCode -ne 0 -and (Test-Path -LiteralPath $bootstrapLog)) {
        Copy-Item -LiteralPath $bootstrapLog -Destination (Join-Path $testRoot ($runId + '.bootstrap.log'))
        if (Test-Path -LiteralPath $serverLog) {
            Copy-Item -LiteralPath $serverLog -Destination (Join-Path $testRoot ($runId + '.postgres.log'))
        }
        Write-Host 'Setup logs retained under target/postgres-tests for local troubleshooting.'
    }
    # No shell crossing and no wildcard deletion. Recheck the final path and reparse points.
    $resolved = [IO.Path]::GetFullPath($runRoot)
    $reparse = @(Get-ChildItem -LiteralPath $runRoot -Force -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint })
    if ($canRemove -and $resolved.StartsWith($testRoot + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolved -Leaf) -eq $runId -and
            $reparse.Count -eq 0 -and
            -not ((Get-Item -LiteralPath $resolved).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
        Write-Host 'Disposable PostgreSQL stopped and its run directory removed.'
    } else {
        $exitCode = 1
        Write-Warning 'Disposable run directory retained for safe manual inspection.'
    }
}
exit $exitCode
