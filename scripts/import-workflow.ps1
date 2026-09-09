$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$workflow = Join-Path $root 'image-workflow\n8n-workflow.json'

$container = docker compose --project-directory $root --env-file (Join-Path $root '.env') ps -q n8n
if (-not $container) { throw 'n8n container is not running.' }

docker cp $workflow "${container}:/tmp/n8n-workflow.json"
docker exec $container n8n import:workflow --input=/tmp/n8n-workflow.json
if ($LASTEXITCODE -ne 0) { throw 'n8n workflow import failed.' }

docker exec $container n8n publish:workflow --id=7bF1r4MeLd7YY3UU
if ($LASTEXITCODE -ne 0) { throw 'n8n workflow activation failed.' }

docker compose --project-directory $root --env-file (Join-Path $root '.env') restart n8n
Write-Output 'My workflow 3 imported and activated.'
