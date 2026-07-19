# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.4.0] - 2026-07-19

### Changed
- 构建目标升级到 IntelliJ IDEA 2026.2 和 Java 25
- 替换已废弃的 IntelliJ Platform 和 Apache Commons Lang API, 提升后续版本兼容性

### Fixed
- 移除旧版 Apache Commons Collections API 调用, 修复 IDEA 2026.2 中 Redis Helper 工具窗口初始化失败的问题

## [1.3.0] - 2026-05-24

### Added
- SSH 隧道功能, 支持通过 SSH 连接到 Redis 服务器, 包括密码认证和密钥认证两种方式
- SSL/TLS 支持, 允许用户通过安全连接访问 Redis 服务器
- Redis Cluster 模式支持, 包括集群节点端点映射
- Console 输出搜索、命令历史和更完整的命令解析

### Changed
- 重构 Console 展示布局, 以卡片形式展示命令执行结果
- Info 对话框改为异步加载 Redis INFO 各 section
- 连接树加载改为异步读取连接配置

### Fixed
- SSH Tunnel 默认启用主机密钥校验, 避免静默信任未知跳板机
- Cluster 模式端点映射缓存改为线程安全容器
- Cluster Console 对跨 slot 多 key 命令返回明确错误
- 连接树异步加载失败时记录日志并显示错误提示

## [1.2.0]

### Changed
- 优化 Filter 搜索体验: 直接输入关键字即可搜索, 无需手动输入 `*`, 查询时会自动补全前后通配符
- Filter 搜索支持停止输入一小段时间后自动触发, 同时忽略前后空格
- 优化 Filter 输入框宽度, 会根据当前输入内容自动扩展
- 升级 Jedis 到 `5.2.0`
- 升级 IntelliJ Platform Gradle Plugin 和 Gradle Wrapper, 适配最新构建工具链


## [1.1.0] - 2024-11-16
### Changed
- 更新过期的 API 调用, 适配 2024.3

## [1.0.0] - 2023-09-13

### Fixed
- 百万数据量 DB 打开很慢的问题
- 解决 "EventQueue.isDispatchThread()=false" 的问题
- "Test Connection" 的 NPE 问题

### Changed
- Flush DB 时添加复杂的确认方式
- 更新过期的 API 调用
- 使用 Java 17

### Added
- Plugin logo

### Removed
- key 的分页展示

## [beta-0.9.0] - 2022-09-18

### Changed
- 更新 readme.md
- 优化 `RedisPoolManager` 代码
- 删除不用的代码
- 升级 Jedis 版本到 4.2.3
- 使用 `PasswordSafe` 保存密码

### Added
- `Connection Settings` 中支持配置 `Username`
- 一些边界判断 @yizhishi


## [beta-0.8.0] - 2022-06-25

### Fixed

- 使用第三方主题时, 背景错误
- 开启链接的时候 因为读DB下的key数量导致整个软件卡住 (Loading 时冻结 UI)

### Changed
- 更新过时的API
- 最低可用版本更新 221.5921.22(2022.1.3)

## [beta-0.7.0]

### Changed

- Dialog的OK/Cancel按钮会根据操作系统选择不同的排列顺序

## [beta-0.6.0] - 2021-11-28

### Added

- CHANGELOG.md
- Redis连接右键菜单添加"Info"功能
- 连接配置可以设为全局

### Changed

- 修正redis console行高
- Use jackson to serialize json

### Removed

- fastjson

### Fixed

- duplicate connection bug

## [beta-0.5.0] - 2021-10-30

### Added

- redis console(The 'RESTORE' command and blocking commands such as 'SUBSCRIBE' are not supported)

### Changed

- reload connection bug
- query the number of DBs without using the 'CONFIG GET' command
- replace Label with Jlabel

[1.4.0]: https://github.com/MattMin/a-redis/releases/tag/1.4.0

[1.3.0]: https://github.com/MattMin/a-redis/releases/tag/1.3.0

[1.2.0]: https://github.com/MattMin/a-redis/releases/tag/1.2.0

[1.1.0]: https://github.com/MattMin/a-redis/releases/tag/1.1.0

[1.0.0]: https://github.com/MattMin/a-redis/releases/tag/1.0.0

[beta-0.9.0]: https://github.com/MattMin/a-redis/releases/tag/beta-0.9.0

[beta-0.8.0]: https://github.com/MattMin/a-redis/releases/tag/beta-0.8.0

[beta-0.7.0]: https://github.com/MattMin/a-redis/releases/tag/beta-0.7.0

[beta-0.6.0]: https://github.com/MattMin/a-redis/releases/tag/beta-0.6.0

[beta-0.5.0]: https://github.com/MattMin/a-redis/releases/tag/beta-0.5.0
