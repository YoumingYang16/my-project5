$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$template = Join-Path $root '.env.example'
$target = Join-Path $root '.env'

if (Test-Path -LiteralPath $target) {
    Write-Output '.env already exists; it was not overwritten.'
    exit 0
}

function New-RandomSecret([int]$bytes = 32) {
    $buffer = New-Object byte[] $bytes
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($buffer) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($buffer).Replace('+', '-').Replace('/', '_').TrimEnd('=')
}

$content = Get-Content -LiteralPath $template -Raw
$secretMarker = 'generated-by-setup'
while ($content.Contains($secretMarker)) {
    $marker = $content.IndexOf($secretMarker)
    $content = $content.Remove($marker, $secretMarker.Length).Insert($marker, (New-RandomSecret 32))
}
$adminPassword = New-RandomSecret 18
$content = $content.Replace('generated-admin-password', $adminPassword)
[System.IO.File]::WriteAllText($target, $content, (New-Object System.Text.UTF8Encoding($false)))
Write-Output 'Created .env with independent encryption and JWT secrets.'
Write-Output "Bootstrap admin username: admin"
Write-Output "Bootstrap admin password: $adminPassword"
Write-Output 'Store this password securely; it is only shown during setup.'
Write-Output 'DeepSeek and Qwen keys are intentionally blank; enter them in the website Settings page.'
