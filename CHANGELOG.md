# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

### Removed

[Unreleased]: https://github.com/MattMin/a-redis/compare/beta-0.6.0...HEAD
[beta-0.6.0]: https://github.com/MattMin/a-redis/releases/tag/beta-0.6.0
[beta-0.5.0]: https://github.com/MattMin/a-redis/releases/tag/beta-0.5.0
