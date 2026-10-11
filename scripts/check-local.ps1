[CmdletBinding()]
param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
if ($Port -lt 1 -or $Port -gt 65535) { throw 'Invalid application port.' }
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(15)
function Check-Response {
    param([string]$Method, [string]$Path, [int]$Expected, [string]$Body, [string]$Bearer)
    $request = New-Object System.Net.Http.HttpRequestMessage
    $request.Method = New-Object System.Net.Http.HttpMethod($Method)
    $request.RequestUri = [Uri]("http://localhost:$Port$Path")
    if ($Body) { $request.Content = New-Object System.Net.Http.StringContent($Body, [Text.Encoding]::UTF8, 'application/json') }
    if ($Bearer) { $request.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $Bearer) }
    try {
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        try {
            $actual = [int]$response.StatusCode
            if ($actual -ne $Expected) { throw "Check failed: $Method $Path expected $Expected, received $actual." }
            $payload = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
            $names = @($payload.PSObject.Properties.Name | Sort-Object)
            if ($Expected -eq 200) {
                if ($names.Count -ne 1 -or $names[0] -ne 'status' -or $payload.status -ne 'UP') { throw 'Unexpected liveness response.' }
            } else {
                if (($names -join ',') -ne 'code,message,status,success' -or $payload.success -ne $false -or
                    $payload.status -ne $Expected -or -not $payload.code -or -not $payload.message) { throw 'Unexpected error contract.' }
            }
            Write-Host "PASS $Method $Path ($actual)"
        } finally { $response.Dispose() }
    } finally { $request.Dispose() }
}
try {
    Check-Response GET '/api/health/live' 200
    Check-Response GET '/api/health/live' 200 -Bearer 'synthetic-invalid-token'
    Check-Response GET '/api/health' 401
    Check-Response GET '/api/health' 401 -Bearer 'synthetic-invalid-token'
    Check-Response POST '/api/auth/login' 400 -Body '{'
    Check-Response POST '/api/auth/login' 400 -Body '{"email":"invalid","password":"synthetic-only"}'
    $email = 'unknown-' + [Guid]::NewGuid().ToString('N') + '@example.invalid'
    $body = @{ email = $email; password = 'synthetic-only' } | ConvertTo-Json -Compress
    Check-Response POST '/api/auth/login' 401 -Body $body
    Write-Host 'All 7 live checks passed. No users were created or changed.'
} finally { $client.Dispose() }
