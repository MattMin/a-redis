#!/usr/bin/env zsh
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
BASE_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

cd "$BASE_DIR"

echo "[a-redis] 启动 Redis Cluster 测试环境..."
docker compose down -v --remove-orphans >/dev/null 2>&1 || true
docker compose up -d

echo "[a-redis] 等待集群初始化完成..."
for i in {1..30}; do
  if redis-cli -c -h 127.0.0.1 -p 7001 cluster info 2>/dev/null | grep -q 'cluster_state:ok'; then
    echo "[a-redis] Redis Cluster 已就绪。"
    exit 0
  fi
  sleep 2
done

echo "[a-redis] 集群启动超时，请执行 docker compose logs 查看详细日志。" >&2
exit 1

