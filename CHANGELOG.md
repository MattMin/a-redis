# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## TODO
- Support for TLS

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

[Unreleased]: https://github.com/MattMin/a-redis/compare/beta-0.9.0...dev-1.0

[beta-0.9.0]: https://github.com/MattMin/a-redis/releases/tag/beta-0.9.0

[beta-0.8.0]: https://github.com/MattMin/a-redis/releases/tag/beta-0.8.0

[beta-0.7.0]: https://github.com/MattMin/a-redis/releases/tag/beta-0.7.0

[beta-0.6.0]: https://github.com/MattMin/a-redis/releases/tag/beta-0.6.0

[beta-0.5.0]: https://github.com/MattMin/a-redis/releases/tag/beta-0.5.0
