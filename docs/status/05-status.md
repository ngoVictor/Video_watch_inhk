# 当前基线

线上版本为 [0.5.0-alpha / 20](https://github.com/ngoVictor/Video_watch_inhk/releases/tag/v0.5.0-alpha)，应用源码标签6c1e940，包名 hk.jiakan.tv，原签名保持。公开 APK SHA-256：c33f197a51107e41edbf8182351d92fa4ad2e19b8a587c2febeb96f57f54fc32。公开发行不带频道或凭据；完整依赖源码分卷附在 Release。

本轮仅修改源码打包工具并整理规范和使用文档，没有修改 Android/core 运行代码，没有重发 APK 或更新线上清单。修复内容：公开源码包拒绝非空内置频道和 .private 目录；与 APK 使用相同目录检查。旧 APK 的播放和频道数据不受影响。

私用配置、签名与家庭安装包留在本仓库忽略目录。首次迁移采用 U 盘覆盖安装；以后软件和频道独立更新。没有家庭实机或长时观看验证。

验证和清理结果见 [本次审查](../reviews/2026-11-03-baseline.md)。

本轮回归：频道Python74、App工具Python18、core32/Android Debug97通过，Lint0错误/9警告，Debug构建及两仓文档检查通过。线上仍为0.5.0；本轮仅工具和文档整改。
