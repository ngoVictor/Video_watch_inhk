家看电视 0.5.0-alpha：独立开源客户端。

- 频道生产与 App 分离，公开 APK 不包含频道或私有凭据。
- 在维护页配置 HTTPS JSON 目录，私有 GitHub 目录可单独设置读取凭据。
- 频道凭据仅绑定目录地址，软件更新不携带它；频道缓存、收藏和播放功能保留。
- 空目录启动显示配置说明，首次下载目录后可选台播放。
- core 32、Android Debug/Release 各 97 单测通过；Lint 0 错误、9 项既有警告；构建通过。
- 未执行家庭设备安装或长时观看验收。

自有源码 MIT；随附音频依赖按其各自许可证提供固定版本源码，详情见项目 publishing/audio-dependency-sources.json 和 assets/licenses。
