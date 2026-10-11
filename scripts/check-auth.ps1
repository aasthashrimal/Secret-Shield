[CmdletBinding()]
param(
    [int]$Port = 8080,
    [switch]$CreateSyntheticUser,
    [string]$FixtureId = [Guid]::NewGuid().ToString('N')
)
$ErrorActionPreference = 'Stop'
if (-not $CreateSyntheticUser) { throw 'Use -CreateSyntheticUser to explicitly allow creating one synthetic demo account.' }
if ($Port -lt 1 -or $Port -gt 65535 -or $FixtureId -cnotmatch '^[a-f0-9]{32}$') { throw 'Invalid port or synthetic fixture ID.' }
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(15)
$username = 'demo-' + $FixtureId.Substring(0, 12)
$email = $FixtureId + '@example.invalid'
$password = 'synthetic-' + [Guid]::NewGuid().ToString('N')
function Send-Check {
    param([string]$Method, [string]$Path, [int]$Expected, [object]$Body, [string]$Token,
        [string]$Origin, [switch]$Preflight)
    $request = New-Object System.Net.Http.HttpRequestMessage
    $request.Method = New-Object System.Net.Http.HttpMethod($Method)
    $request.RequestUri = [Uri]("http://localhost:$Port$Path")
    if ($Body) {
        $encoded = $Body | ConvertTo-Json -Compress
        $request.Content = New-Object System.Net.Http.StringContent($encoded, [Text.Encoding]::UTF8, 'application/json')
    }
    if ($Token) { $request.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $Token) }
    if ($Origin) { [void]$request.Headers.TryAddWithoutValidation('Origin', $Origin) }
    if ($Preflight) {
        [void]$request.Headers.TryAddWithoutValidation('Access-Control-Request-Method', 'GET')
        [void]$request.Headers.TryAddWithoutValidation('Access-Control-Request-Headers', 'Authorization')
    }
    try {
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        try {
            $actual = [int]$response.StatusCode
            if ($actual -ne $Expected) { throw "Check failed: $Method $Path expected $Expected, received $actual." }
            if ($Expected -ge 400) {
                $errorBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
                if ($errorBody.status -ne $Expected -or $errorBody.success -ne $false -or -not $errorBody.code) {
                    throw 'Unexpected safe error contract.'
                }
            }
            if ($Origin -and $Expected -ne 403) {
                if (-not $response.Headers.Contains('Access-Control-Allow-Origin') -or
                        @($response.Headers.GetValues('Access-Control-Allow-Origin'))[0] -ne $Origin -or
                        $response.Headers.Contains('Access-Control-Allow-Credentials')) { throw 'Unexpected CORS response.' }
            }
            if ($response.Headers.Contains('Set-Cookie')) { throw 'Unexpected session cookie.' }
            Write-Host "PASS $Method $Path ($actual)"
            return $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        } finally { $response.Dispose() }
    } finally { $request.Dispose() }
}
try {
    $null = Send-Check GET '/api/auth/me' 401
    $null = Send-Check POST '/api/auth/register' 400 -Body @{username='ab'; email=$email; password=$password}
    $registration = Send-Check POST '/api/auth/register' 201 -Body @{username=$username; email=(' ' + $email.ToUpperInvariant() + ' '); password=$password}
    $registered = $registration | ConvertFrom-Json
    if ($registered.email -ne $email -or $registered.role -ne 'DEVELOPER' -or $registration.Contains($password)) { throw 'Unexpected registration response.' }
    $null = Send-Check POST '/api/auth/register' 409 -Body @{username=$username; email=$email; password=$password}
    $null = Send-Check POST '/api/auth/login' 401 -Body @{email=$email; password='synthetic-wrong-password'}
    $null = Send-Check POST '/api/auth/login' 401 -Body @{email=('unknown-' + $email); password=$password}
    $login = Send-Check POST '/api/auth/login' 200 -Body @{email=(' ' + $email.ToUpperInvariant() + ' '); password=$password}
    $token = ($login | ConvertFrom-Json).token
    if (-not $token) { throw 'Login did not return a token.' }
    $me = Send-Check GET '/api/auth/me' 200 -Token $token | ConvertFrom-Json
    if (@($me.PSObject.Properties).Count -ne 4 -or $me.email -ne $email -or $me.id -isnot [string]) { throw 'Unexpected safe me response.' }
    $null = Send-Check GET '/api/health' 200 -Token $token
    $null = Send-Check GET '/api/auth/me' 401 -Token ($token + 'synthetic-tamper')
    $origin = if ($env:FRONTEND_ORIGIN) { $env:FRONTEND_ORIGIN } else { 'http://localhost:5173' }
    $null = Send-Check OPTIONS '/api/auth/me' 200 -Origin $origin -Preflight
    $null = Send-Check GET '/api/auth/me' 200 -Token $token -Origin $origin
    $null = Send-Check OPTIONS '/api/auth/me' 403 -Origin 'https://untrusted.example.invalid' -Preflight
    $token = $null
    $null = Send-Check GET '/api/auth/me' 401
    Write-Host 'All 14 authentication checks passed. No passwords or tokens were printed.'
    Write-Host ('Synthetic demo account remains: ' + $email)
} finally { $password = $null; $token = $null; $client.Dispose() }
