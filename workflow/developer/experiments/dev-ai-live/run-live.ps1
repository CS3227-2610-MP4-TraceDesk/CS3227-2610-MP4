<#
  Loads .env.developer-live from the repository root into this process only
  (values are never printed) and runs the guarded Developer live evaluation.

  -Fingerprint  only prints the 16-hex SHA-256 fingerprint of SOCLAAS_API_KEY
                for the approval file; makes no AI call.
#>
param([switch]$Fingerprint)
$ErrorActionPreference = 'Stop'
$root = (git rev-parse --show-toplevel).Trim()
$envFile = Join-Path $root '.env.developer-live'
if (-not (Test-Path $envFile)) { throw "Missing $envFile - copy developer-live.env.example there and fill it in." }

$missing = @()
foreach ($line in Get-Content -LiteralPath $envFile) {
    $t = $line.Trim()
    if ($t -eq '' -or $t.StartsWith('#')) { continue }
    $i = $t.IndexOf('=')
    if ($i -lt 1) { continue }
    $name = $t.Substring(0, $i).Trim()
    $value = $t.Substring($i + 1).Trim()
    if ($value -eq '') { $missing += $name; continue }
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}
if ($missing.Count -gt 0) { throw "Empty values in .env.developer-live: $($missing -join ', ')" }

if ($Fingerprint) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $bytes = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($env:SOCLAAS_API_KEY))
    (($bytes | ForEach-Object { $_.ToString('x2') }) -join '').Substring(0, 16)
    exit 0
}

Set-Location $root
.\gradlew.bat --no-daemon :backend:auth-app:developerAiLiveEvaluation --console=plain
exit $LASTEXITCODE
