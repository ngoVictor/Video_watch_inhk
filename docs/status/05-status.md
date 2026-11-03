# 当前实现与验证

2026-11-03：完成频道生产与 App 拆分。公开源码从现有应用建立新历史，不导入私人数据及旧 Git 历史。版本 0.5.0-alpha / 20，包名 hk.jiakan.tv，原发行签名保持。

- core 32 项；Android Debug/Release 各 97 项；Python 16 项通过。
- Debug/Release Lint 均 0 错误、9 警告；Debug、androidTest 构建和原签名公开/私用 Release 构建通过。androidTest 仅构建，未在设备执行。
- 公开源码及 APK 检查：无当前私人线路、已知凭据或私人仓库引用；公开内置目录为空。公共 APK SHA-256：c33f197a51107e41edbf8182351d92fa4ad2e19b8a587c2febeb96f57f54fc32。
- 私用目录输入已读取并匹配上游目录；实际私用 APK 保留同包名与签名，仅本地交付，不公开上传。
- 保留原播放策略、收藏及有效目录缓存。首次迁移采用 U 盘覆盖安装，之后应用更新与目录更新独立。

没有操作家庭设备；编译、协议、HTTP 和资产检查不能证明香港家庭长时播放。频道质量由外部目录提供者负责。本次发布状态以 App 仓库 Release 和 publishing/update.json 为准。

## 最终本地布局验证

工作区容器下的 Video_watch_inhk 为独立 App 仓库，与频道仓库并列。移位后重新执行 core、Android Debug 单测、Lint 和 APK 构建全部通过；Python16和文档检查通过。家庭 APK 保存在本仓库 artifacts/release/jiakan-tv-0.5.0-alpha-private.apk，相关配置和签名在被忽略的 .tools 中。


## 公开发行完成

[0.5.0-alpha / 20](https://github.com/ngoVictor/Video_watch_inhk/releases/tag/v0.5.0-alpha) 已正式发布；无凭据公开下载的 APK 与本地已验签产物大小和 SHA-256 一致。19 个附件逐项大小/散列核验通过，包括 14 个完整源码分卷；运行 Release 附件 restore-source.py 自动下载、校验并还原完整源码 ZIP。源码标签对应提交 6c1e94041a227a5dcb69f525f31261b0a137c757；后续文档与发布清单独立提交。publishing/update.json 已指向新仓库公开 APK。私用 APK 没有上传。
