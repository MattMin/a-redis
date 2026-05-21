# Local Redis TLS + SSH Tunnel Test Environment

这个目录提供一套可重复启动的本地测试环境，用来验证 A-Redis 的 `SSL/TLS` 和 `SSH Tunnel` 功能。

## Directory Layout

- `docker-compose.yml`：启动全部容器
- `redis/tls/redis.conf`：TLS Redis 配置
- `redis/private/redis.conf`：仅供 SSH 跳板访问的私有 Redis 配置
- `ssh-jump/`：SSH Jump Host 容器定义
- `scripts/generate-certs.sh`：生成本地 CA 和 Redis 服务端证书
- `scripts/generate-ssh-keys.sh`：生成 SSH 测试密钥
- `scripts/up.sh`：生成证书/密钥并启动环境
- `scripts/down.sh`：停止环境
- `scripts/smoke-test.sh`：自动验证 TLS 和 SSH 隧道是否可用
- `scripts/test-ssh-password.sh`：自动验证 SSH 密码认证 + 隧道转发是否可用

## What Gets Started

启动后会有 3 个服务：

1. `redis-tls`
   - 对宿主机暴露 `6380`
   - 启用 TLS
   - Redis 密码：`redis-tls-pass`

2. `redis-private`
   - 仅在 Docker 内部网络暴露 `6379`
   - 不直接映射到宿主机
   - Redis 密码：`redis-private-pass`

3. `ssh-jump`
   - 对宿主机暴露 `2222`
   - SSH 用户：`tunnel`
   - SSH 密码：`tunnel-pass`
   - 同时信任脚本生成的公钥

## Quick Start

在项目根目录执行：

```zsh
cd /Users/matt/workspace/matt/a-redis/dev-env/redis-tls-ssh
chmod +x scripts/*.sh ssh-jump/entrypoint.sh
./scripts/up.sh
./scripts/smoke-test.sh
./scripts/test-ssh-password.sh
```

停止环境：

```zsh
cd /Users/matt/workspace/matt/a-redis/dev-env/redis-tls-ssh
./scripts/down.sh
```

如果你想重新生成证书或密钥：

```zsh
cd /Users/matt/workspace/matt/a-redis/dev-env/redis-tls-ssh
./scripts/generate-certs.sh --force
./scripts/generate-ssh-keys.sh --force
```

## Generated Files

首次执行 `./scripts/up.sh` 后会自动生成：

- CA 证书：`certs/ca.crt`
- CA 私钥：`certs/ca.key`
- Redis 服务端证书：`certs/redis-server.crt`
- Redis 服务端私钥：`certs/redis-server.key`
- SSH 私钥（无口令）：`ssh/client/id_ed25519`
- SSH 私钥（带口令）：`ssh/client/id_ed25519_pp`
  - passphrase: `aredis-passphrase`

> `certs/*.crt`、`certs/*.key`、`ssh/client/*` 已在当前目录的 `.gitignore` 中忽略，只保留在你的本地工作区。

## A-Redis Connection Test Cases

下面是建议的最小测试矩阵。

### 1. SSL/TLS - Trust All Certificates

用于验证：
- `sslTls = true`
- `sslTrustAllCertificates = true`

填写方式：

- Connection Name: `local-redis-tls-trust-all`
- Host: `localhost`
- Port: `6380`
- Password: `redis-tls-pass`
- Username: 留空
- Enable SSL/TLS: 勾选
- Trust all certificates: 勾选
- Verify hostname: 勾选
- CA File: 留空
- Client Cert: 留空

预期：`Test Connection` 成功。

### 2. SSL/TLS - Custom CA File

用于验证：
- `sslTls = true`
- `sslTrustAllCertificates = false`
- `sslTruststorePath = certs/ca.crt`
- `sslVerifyHostname = true`

填写方式：

- Connection Name: `local-redis-tls-ca`
- Host: `localhost`
- Port: `6380`
- Password: `redis-tls-pass`
- Enable SSL/TLS: 勾选
- Trust all certificates: 不勾选
- Verify hostname: 勾选
- CA File: `/Users/matt/workspace/matt/a-redis/dev-env/redis-tls-ssh/certs/ca.crt`
- CA Password: 留空

预期：`Test Connection` 成功。

### 3. SSH Tunnel - Password Auth

用于验证：
- `sshTunnel = true`
- `tunnelPassword`

填写方式：

- Connection Name: `local-redis-ssh-password`
- Host: `redis-private`
- Port: `6379`
- Password: `redis-private-pass`
- Enable SSH Tunnel: 勾选
- SSH Host: `localhost`
- SSH Port: `2222`
- SSH Username: `tunnel`
- Auth Type: `Password`
- SSH Password: `tunnel-pass`

预期：`Test Connection` 成功。

### 4. SSH Tunnel - Private Key Auth

用于验证：
- `sshTunnel = true`
- `tunnelPrivateKeyPath`
- `tunnelPassphrase`

填写方式：

- Connection Name: `local-redis-ssh-key`
- Host: `redis-private`
- Port: `6379`
- Password: `redis-private-pass`
- Enable SSH Tunnel: 勾选
- SSH Host: `localhost`
- SSH Port: `2222`
- SSH Username: `tunnel`
- Auth Type: `Private Key`
- Private Key File: `/Users/matt/workspace/matt/a-redis/dev-env/redis-tls-ssh/ssh/client/id_ed25519_pp`
- Private Key Password: `aredis-passphrase`

预期：`Test Connection` 成功。

## Notes

1. SSH 场景里，Redis `Host` 需要填 `redis-private`，不是 `localhost`。
   因为当前实现会在 SSH 服务器侧转发到 `hostField:portField`，对应代码在 `RedisPoolManager#getConnectionEndpoint()`。

2. TLS 场景里，建议先用 `localhost:6380`。
   当前生成的服务端证书包含 `localhost` 和 `127.0.0.1` 的 SAN。

3. 当前环境默认只覆盖：
   - TLS 单向认证
   - SSH 密码认证
   - SSH 私钥认证

4. 当前没有启用 Redis ACL 用户名，`Username` 字段可以留空。

