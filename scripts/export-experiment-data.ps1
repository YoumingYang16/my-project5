param(
    [string]$Destination = (Join-Path $PSScriptRoot "..\experiment-data-export")
)

$ErrorActionPreference = "Stop"
$container = docker compose ps -q website
if (-not $container) {
    throw "PaperLens website container is not running."
}

$resolved = [System.IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Force -Path $resolved | Out-Null
docker cp "${container}:/app/uploads/." $resolved

Write-Host "Experiment data exported to: $resolved"
Write-Host "Participant index: $(Join-Path $resolved 'participant-records')"
Write-Host "Copy and image outputs: $(Join-Path $resolved 'generated-covers')"
