# 家看电视

开源 Android 电视直播客户端，支持遥控器、分类、自动恢复、收藏、节目单和部分回看。**不附带频道或私有访问凭据**；使用者自行配置符合[目录协议](docs/specification/03-data-contracts.md)的 HTTPS 频道目录。菜单/设置键打开维护页，设置目录地址，私有 GitHub 目录另设读取凭据。没有频道时也可打开设置。

配置 JDK 17 和 Android SDK 35 / build-tools 35.0.0，设置 JAVA_HOME、ANDROID_HOME（或 local.properties 中 sdk.dir）。Windows 执行 `./gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`；Linux/macOS 使用 `sh ./gradlew`。不需要私人频道仓库、Python、原作者密钥或隐藏工具。

[文档导航](docs/README.md) · [构建与发行](docs/development/06-build-and-release.md) · [当前验证](docs/status/05-status.md)

本仓库从现有应用代码建立新历史；私人数据与旧研究历史没有导入。自有代码依 LICENSE；第三方依赖许可保留于 app/src/main/assets/licenses，音频依赖源代码地址及散列见 publishing/audio-dependency-sources.json。
