# 构建与发行

标准构建见根 README。公开构建默认 0.5.0-alpha / 20，可用 appVersionCode/appVersionName 覆盖。私用构建需显式传 -PprivateCatalogDir=<绝对路径>，该目录提供 channels.json、catalog-config.json（catalogUrl 字段）及 private-update-token.txt；路径、内容、产物均不得提交或公开。普通公开构建不读取这些文件。

保留包名 hk.jiakan.tv。自建发行者使用自己的签名密钥；原作者家庭覆盖安装使用既有原签名。签名由 JIAKAN_KEYSTORE / JIAKAN_STORE_PASSWORD 环境变量输入，绝不入 Git。`scripts/release.ps1` 为原作者本地发行助手，普通开发构建不需要它。

公共 APK 使用 App 仓库 Releases；发布工具检查 APK 没有私人资产/非空频道，上传 APK 后再生成 publishing/update.json。私用 APK 通过 U 盘交付，禁止上传公开 Release；之后可安装同签名公开更新，已保存在设备上的目录配置、凭据和缓存保留。用户改成自己的更新服务器时应继续使用同一签名。

Python 工具：`python -m unittest discover -s scripts/tests`、`python scripts/check-docs.py`。源码打包工具需要将 publishing/audio-dependency-sources.json 所列未修改第三方源码置于 artifacts/source-dependencies 并核对散列，以履行分发对应源码要求。

本仓库不包含频道发现、检测和维护任务；协议兼容的目录更新无需重新构建 APK。当前目录来源切换仍遵循契约中 revision 限制。

公开源码包也检查内置频道为空，拒绝 .private 与私用配置。`github-release.py --dry-run` 只预览元数据、不执行上传，不能替代正式发布检查。0.5.0 完整源码已分卷发布，可从 Release 下载 restore-source.py 自动下载并校验还原。
