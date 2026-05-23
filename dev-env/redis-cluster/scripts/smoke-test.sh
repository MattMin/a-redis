#!/usr/bin/env zsh
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
BASE_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

HOST=${1:-redis-node-1}
PORT=${2:-6379}
KEY="aredis:cluster:smoke:$(date +%s)"
VALUE="ok"

cd "$BASE_DIR"

echo "[a-redis] 检查集群状态..."
docker compose exec -T redis-node-1 redis-cli -c -h "$HOST" -p "$PORT" cluster info | grep 'cluster_state:ok'

echo "[a-redis] 写入测试 key: $KEY"
docker compose exec -T redis-node-1 redis-cli -c -h "$HOST" -p "$PORT" set "$KEY" "$VALUE" >/dev/null

ACTUAL=$(docker compose exec -T redis-node-1 redis-cli -c -h "$HOST" -p "$PORT" get "$KEY" | tr -d '\r')
if [ "$ACTUAL" != "$VALUE" ]; then
  echo "[a-redis] 冒烟测试失败：读取值不匹配，actual=$ACTUAL" >&2
  exit 1
fi

echo "[a-redis] 删除测试 key: $KEY"
docker compose exec -T redis-node-1 redis-cli -c -h "$HOST" -p "$PORT" del "$KEY" >/dev/null

echo "[a-redis] Redis Cluster 冒烟测试通过。"

