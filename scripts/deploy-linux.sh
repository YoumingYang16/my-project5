#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f .env ]]; then
  echo "Missing .env. Copy .env.production.example to .env and fill the domain and secrets first." >&2
  exit 1
fi

docker compose \
  --env-file .env \
  -f docker-compose.yml \
  -f docker-compose.production.yml \
  config --quiet

docker compose \
  --env-file .env \
  -f docker-compose.yml \
  -f docker-compose.production.yml \
  up -d --build

docker compose \
  --env-file .env \
  -f docker-compose.yml \
  -f docker-compose.production.yml \
  ps
