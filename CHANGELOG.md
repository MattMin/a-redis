# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- CHANGELOG.md

### Changed
- 修正redis console行高
- Use jackson to serialize json

### Removed
- fastjson

## [beta-0.5.0] - 2021-10-30
### Added
- redis console(The 'RESTORE' command and blocking commands such as 'SUBSCRIBE' are not supported)

### Changed
- reload connection bug
- query the number of DBs without using the 'CONFIG GET' command
- replace Label with Jlabel

### Removed

[Unreleased]: https://github.com/MattMin/a-redis/compare/beta-0.5.0...HEAD
[beta-0.5.0]: https://github.com/MattMin/a-redis/releases/tag/beta-0.5.0
