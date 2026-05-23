# Local Redis Cluster Test Environment

这个目录提供一套可重复启动的本地 Redis Cluster 环境，用来验证 A-Redis 的集群连接能力。

这套环境的集群节点在 Docker 内部通过 `redis-node-*` service hostname 通信，A-Redis 侧通过插件内新增的 host/port 映射逻辑，仍然可以直接使用宿主机地址 `127.0.0.1:7001` 接入并自动发现其它节点。

## 目录说明

- `docker-compose.yml`：启动 6 个 Redis 节点并自动组建 Cluster
- `scripts/up.sh`：启动并等待集群就绪
- `scripts/down.sh`：停止并清理容器、数据卷
- `scripts/smoke-test.sh`：执行最小可用性验证

## 集群拓扑

启动后会创建一个 `3 主 3 从` 的 Redis Cluster：

- `127.0.0.1:7001`
- `127.0.0.1:7002`
- `127.0.0.1:7003`
- `127.0.0.1:7004`
- `127.0.0.1:7005`
- `127.0.0.1:7006`

同时会暴露对应的 cluster bus 端口：

- `17001` ~ `17006`

> 说明：容器内节点通过 `redis-node-*` service hostname 互相通信；插件侧已内置针对这套测试环境的端口映射逻辑，所以宿主机上的 A-Redis 仍可直接从 `127.0.0.1:7001` 接入并跟随集群路由。

## 快速启动

```zsh
cd /Users/matt/workspace/matt/a-redis/dev-env/redis-cluster
chmod +x scripts/*.sh
./scripts/up.sh
./scripts/smoke-test.sh
```

停止环境：

```zsh
cd /Users/matt/workspace/matt/a-redis/dev-env/redis-cluster
./scripts/down.sh
```

## 在 A-Redis 中的连接配置

推荐先用下面这组参数测试：

- Connection Name: `local-redis-cluster`
- Host: `127.0.0.1`
- Port: `7001`
- Password: 留空
- Username: 留空
- Cluster 页签：勾选 `Enable Cluster Mode`
- SSH Tunnel：不勾选
- SSL/TLS：不勾选

> 如果你是通过这套 `dev-env/redis-cluster` 环境测试，请直接填 `127.0.0.1:7001` 即可，不需要填写其它节点。

## 预期行为

完成连接后，你应该能看到：

1. `Test Connection` 成功
2. 连接节点下只展示一个 `DB0`
3. 双击 `DB0` 后可以正常浏览 key
4. 新增、删除、编辑 key/value 可以正常执行
5. `Info` 窗口可以正常打开

## 冒烟验证

如果你想手工验证：

```zsh
redis-cli -h 127.0.0.1 -p 7001 cluster info
docker compose exec -T redis-node-1 redis-cli -c -h redis-node-1 -p 6379 set aredis:demo hello
docker compose exec -T redis-node-1 redis-cli -c -h redis-node-1 -p 6379 get aredis:demo
docker compose exec -T redis-node-1 redis-cli -c -h redis-node-1 -p 6379 del aredis:demo
```

## 当前限制

当前插件中的 Cluster Mode 主要面向：

- 连接测试
- key 树加载
- value 查看与编辑
- 常用命令的基础控制台执行

暂不支持：

- Cluster Mode + SSH Tunnel 组合
- Redis Cluster 的多 DB 视图（Cluster 固定按 `DB0` 处理）

