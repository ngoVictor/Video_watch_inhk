# 当前实现与验证

2026-11-03：完成频道生产与 App 拆分。公开源码从现有应用建立新历史，不导入私人数据及旧 Git 历史。版本 0.5.0-alpha / 20，包名 hk.jiakan.tv，原发行签名保持。

- core 32 项；Android Debug/Release 各 97 项；Python 16 项通过。
- Debug/Release Lint 均 0 错误、9 警告；Debug、androidTest 构建和原签名公开/私用 Release 构建通过。androidTest 仅构建，未在设备执行。
- 公开源码及 APK 检查：无当前私人线路、已知凭据或私人仓库引用；公开内置目录为空。公共 APK SHA-256：c33f197a51107e41edbf8182351d92fa4ad2e19b8a587c2febeb96f57f54fc32。
- 私用目录输入已读取并匹配上游目录；实际私用 APK 保留同包名与签名，仅本地交付，不公开上传。
- 保留原播放策略、收藏及有效目录缓存。首次迁移采用 U 盘覆盖安装，之后应用更新与目录更新独立。

没有操作家庭设备；编译、协议、HTTP 和资产检查不能证明香港家庭长时播放。频道质量由外部目录提供者负责。本次发布状态以 App 仓库 Release 和 publishing/update.json 为准。
