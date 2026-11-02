# App 架构

app -> core；core 只含平台无关频道模型、播放策略和接口。app/catalog 解析、校验、缓存和刷新目录；app/playback 适配 Media3；app/guide 负责节目单/回看；app/update 独立校验和安装 APK；UI 协调遥控器交互。频道发现、质量审查和生成器属于外部生产者，不进入本仓库。

公开构建内置空目录。显式 -PprivateCatalogDir=<本地目录> 生成私用 assets，目录中 channels.json、catalog-config.json 与 private-update-token.txt 仅进入本地 APK。普通 Release 不自动读取隐藏配置。公开发布工具拒绝包含非空内置目录、配置或 token 的 APK。

目录使用 GitHub contents 时，凭据绑定到配置时的精确 HTTPS 地址；更换地址需重新绑定凭据。HTTP 重定向不携带认证。APK 更新始终不携带目录凭据。Keystore 加密保存设备凭据；不能将加密存储或 APK 内置文件视为不可提取的保密设施。私用 APK 仅交给频道授权使用者。

JDK 17、Gradle 8.11.1、AGP 8.9.2、SDK 35，Media3 1.7.1、Jellyfin FFmpeg 音频扩展 1.6.1+2；视频解码与播放阈值不因拆分改变。
