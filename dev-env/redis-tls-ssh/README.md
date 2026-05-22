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
- `scripts/test-mtls.sh`：自动验证 CA Password / Client Cert / Key Password 对应的 mTLS 资源

## What Gets Started

启动后会有 4 个服务：

1. `redis-tls`
   - 对宿主机暴露 `6380`
   - 启用 TLS
   - Redis 密码：`redis-tls-pass`

2. `redis-mtls`
   - 对宿主机暴露 `6381`
   - 启用 TLS + 双向认证（mTLS）
   - Redis 密码：`redis-mtls-pass`

3. `redis-private`
   - 仅在 Docker 内部网络暴露 `6379`
   - 不直接映射到宿主机
   - Redis 密码：`redis-private-pass`

4. `ssh-jump`
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
./scripts/test-mtls.sh
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
- CA Truststore（PKCS12）：`certs/ca-truststore.p12`
  - password: `aredis-ca-pass`
- Redis 服务端证书：`certs/redis-server.crt`
- Redis 服务端私钥：`certs/redis-server.key`
- Redis mTLS 客户端证书：`certs/client.crt`
- Redis mTLS 客户端私钥：`certs/client.key`
- Redis mTLS 客户端 Keystore（PKCS12）：`certs/client-keystore.p12`
  - password: `aredis-client-pass`
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

### 3. SSL/TLS - CA Password (PKCS12 Truststore)

用于验证：
- `sslTls = true`
- `sslTruststorePath = certs/ca-truststore.p12`
- `sslTruststorePassword = aredis-ca-pass`

填写方式：

- Connection Name: `local-redis-tls-ca-password`
- Host: `localhost`
- Port: `6380`
- Password: `redis-tls-pass`
- Enable SSL/TLS: 勾选
- Trust all certificates: 不勾选
- Verify hostname: 勾选
- CA File: `/Users/matt/workspace/matt/a-redis/dev-env/redis-tls-ssh/certs/ca-truststore.p12`
- CA Password: `aredis-ca-pass`

预期：`Test Connection` 成功。

### 4. SSL/TLS - Client Cert + Key Password (mTLS)

用于验证：
- `sslTls = true`
- `sslTruststorePath = certs/ca-truststore.p12`
- `sslTruststorePassword = aredis-ca-pass`
- `sslKeystorePath = certs/client-keystore.p12`
- `sslKeystorePassword = aredis-client-pass`

填写方式：

- Connection Name: `local-redis-mtls-client-cert`
- Host: `localhost`
- Port: `6381`
- Password: `redis-mtls-pass`
- Enable SSL/TLS: 勾选
- Trust all certificates: 不勾选
- Verify hostname: 勾选
- CA File: `/Users/matt/workspace/matt/a-redis/dev-env/redis-tls-ssh/certs/ca-truststore.p12`
- CA Password: `aredis-ca-pass`
- Client Cert: `/Users/matt/workspace/matt/a-redis/dev-env/redis-tls-ssh/certs/client-keystore.p12`
- Key Password: `aredis-client-pass`

预期：`Test Connection` 成功。

### 5. SSH Tunnel - Password Auth

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

### 6. SSH Tunnel - Private Key Auth

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

3. `CA Password` 只有在 `CA File` 选择的是 `.p12` / `.pfx` / `.jks` 这类 truststore 时才有意义；如果选择的是 `.crt` / `.cer` / `.pem`，请留空。

4. `Client Cert` 当前应该选择 `PKCS12` keystore，例如：`client-keystore.p12`。`Key Password` 与该 keystore 密码保持一致：`aredis-client-pass`。

5. 当前没有启用 Redis ACL 用户名，`Username` 字段可以留空。

## SSL/TLS Additional Test Plan

建议按下面顺序回归，便于快速定位问题：

1. **基线验证**
   - 先跑 `./scripts/smoke-test.sh`
   - 再跑 `./scripts/test-mtls.sh`
   - 确认本地资源和容器本身没问题

2. **只测 CA Password**
   - 连接 `localhost:6380`
   - `CA File` 选 `ca-truststore.p12`
   - `CA Password` 填 `aredis-ca-pass`
   - `Client Cert` / `Key Password` 留空
   - 预期成功

3. **只测 Client Cert + Key Password**
   - 连接 `localhost:6381`
   - `CA File` 仍然选 `ca-truststore.p12`
   - `CA Password` 填 `aredis-ca-pass`
   - `Client Cert` 选 `client-keystore.p12`
   - `Key Password` 填 `aredis-client-pass`
   - 预期成功

4. **负例 1：错误 CA Password**
   - 保持 `localhost:6380`
   - `CA Password` 随便填错一个值
   - 预期失败，错误应发生在 truststore 加载阶段

5. **负例 2：缺少 Client Cert**
   - 连接 `localhost:6381`
   - 只配 `CA File` / `CA Password`
   - `Client Cert` 和 `Key Password` 留空
   - 预期失败，错误应发生在 TLS client authentication 阶段

6. **负例 3：错误 Key Password**
   - 连接 `localhost:6381`
   - `Client Cert` 选 `client-keystore.p12`
   - `Key Password` 故意填错
   - 预期失败，错误应发生在 keystore / key manager 初始化阶段

