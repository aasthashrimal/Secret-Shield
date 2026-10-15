function Import-LocalEnvironment {
    param([string]$Workspace)
    $path = Join-Path $Workspace '.env'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw 'Copy .env.example to .env and fill DB_PASSWORD and JWT_SECRET privately.'
    }
    $values = @{}
    foreach ($line in [IO.File]::ReadAllLines($path)) {
        if ($line -match '^\s*(DB_URL|DB_USERNAME|DB_PASSWORD|JWT_SECRET|FRONTEND_ORIGIN|SCANNER_WORKSPACE|GITLEAKS_PATH|GITLEAKS_CONFIG_PATH|SCANNER_TIMEOUT_SECONDS)\s*=(.*)$') {
            $name = $Matches[1]
            $value = $Matches[2].Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                    ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            if ($values.ContainsKey($name)) { throw 'Duplicate backend variable in .env; remove duplicate entries.' }
            $values[$name] = $value
        }
    }
    if ([string]::IsNullOrWhiteSpace($values['DB_PASSWORD'])) { throw 'DB_PASSWORD is missing from private .env.' }
    if ([string]::IsNullOrWhiteSpace($values['JWT_SECRET']) -or
            [Text.Encoding]::UTF8.GetByteCount($values['JWT_SECRET']) -lt 32) {
        throw 'JWT_SECRET must contain at least 32 UTF-8 bytes in private .env.'
    }
    # Never evaluate shell code, expand values, or print credentials.
    foreach ($name in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET', 'FRONTEND_ORIGIN',
            'SCANNER_WORKSPACE', 'GITLEAKS_PATH', 'GITLEAKS_CONFIG_PATH', 'SCANNER_TIMEOUT_SECONDS')) {
        if ($values.ContainsKey($name) -and -not [string]::IsNullOrWhiteSpace($values[$name])) {
            [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
        }
    }
}
