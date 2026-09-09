$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'

if (-not (Test-Path -LiteralPath $envFile)) {
    & (Join-Path $PSScriptRoot 'setup.ps1')
}

function Get-EnvValue([string]$name, [string]$defaultValue) {
    $line = Get-Content -LiteralPath $envFile | Where-Object { $_ -match "^$([regex]::Escape($name))=" } | Select-Object -Last 1
    if (-not $line) { return $defaultValue }
    $value = ($line -split '=', 2)[1].Trim()
    return $(if ($value) { $value } else { $defaultValue })
}

$websitePort = Get-EnvValue 'WEBSITE_PORT' '8080'
$n8nPort = Get-EnvValue 'N8N_PORT' '5679'
$rendererPort = Get-EnvValue 'RENDERER_PORT' '3001'
$assetsPort = Get-EnvValue 'ASSETS_PORT' '8088'

docker compose --project-directory $root --env-file $envFile up -d --no-build
if ($LASTEXITCODE -ne 0) {
    Write-Output 'A local image is missing; building services for first use...'
    docker compose --project-directory $root --env-file $envFile up -d --build
}
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose startup failed.' }

$n8nReady = $false
for ($i = 0; $i -lt 90; $i++) {
    Start-Sleep -Seconds 2
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$n8nPort/healthz" -TimeoutSec 3
        if ($response.StatusCode -eq 200) { $n8nReady = $true; break }
    } catch {}
}
if (-not $n8nReady) { throw 'n8n did not become ready.' }

& (Join-Path $PSScriptRoot 'import-workflow.ps1')

$websiteReady = $false
for ($i = 0; $i -lt 120; $i++) {
    Start-Sleep -Seconds 2
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$websitePort/" -TimeoutSec 3
        if ($response.StatusCode -eq 200) { $websiteReady = $true; break }
    } catch {}
}
if (-not $websiteReady) { throw 'Website did not become ready.' }

$comfyRoot = Get-EnvValue 'COMFYUI_ROOT' 'D:\自动图片\comfyui'
$comfyPython = Join-Path $comfyRoot 'python_embeded\python.exe'
$comfyMain = Join-Path $comfyRoot 'ComfyUI\main.py'
$comfyUrl = 'http://127.0.0.1:8188/system_stats'
$comfyReady = $false
try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri $comfyUrl -TimeoutSec 3
    $comfyReady = $response.StatusCode -eq 200
} catch {}
if (-not $comfyReady -and (Test-Path -LiteralPath $comfyPython) -and (Test-Path -LiteralPath $comfyMain)) {
    Write-Output 'Starting optional ComfyUI...'
    Start-Process -FilePath $comfyPython -ArgumentList @(
        '-s', $comfyMain, '--windows-standalone-build', '--listen', '127.0.0.1', '--port', '8188',
        '--lowvram', '--disable-api-nodes', '--preview-method', 'none'
    ) -WorkingDirectory $comfyRoot `
      -RedirectStandardOutput (Join-Path $comfyRoot 'paper-platform.stdout.log') `
      -RedirectStandardError (Join-Path $comfyRoot 'paper-platform.stderr.log') `
      -WindowStyle Hidden
    for ($i = 0; $i -lt 90; $i++) {
        Start-Sleep -Seconds 2
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $comfyUrl -TimeoutSec 3
            if ($response.StatusCode -eq 200) { $comfyReady = $true; break }
        } catch {}
    }
}
if (-not $comfyReady) {
    Write-Warning 'ComfyUI is unavailable. Qwen and source-image candidates will remain available.'
}

Write-Output 'Ready:'
Write-Output "  Website:     http://127.0.0.1:$websitePort/"
Write-Output "  n8n:         http://127.0.0.1:$n8nPort/"
Write-Output "  renderer-api http://127.0.0.1:$rendererPort/health"
Write-Output "  assets:      http://127.0.0.1:$assetsPort/"
Write-Output "  ComfyUI:     $(if ($comfyReady) { 'http://127.0.0.1:8188/' } else { 'unavailable (Qwen fallback active)' })"