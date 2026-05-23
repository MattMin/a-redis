#!/usr/bin/env zsh
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
BASE_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

cd "$BASE_DIR"

echo "[a-redis] 停止 Redis Cluster 测试环境..."
docker compose down -v --remove-orphans

