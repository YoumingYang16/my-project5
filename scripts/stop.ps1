$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'
docker compose --project-directory $root --env-file $envFile down
Write-Output 'Services stopped. Persistent volumes were kept.'
