# 项目协作规则

- 文档先行：行为变更先更新 docs/specification，再实现并更新 docs/status/05-status.md。
- 本仓库只负责 App，禁止提交私人频道表、目录地址、凭据、签名密钥或私用 APK。
- core 不依赖 Android、UI 或 HTTP；目录、播放和应用更新各自管理状态。
- 保持稳定频道 ID；频道目录的质量证据由目录提供者负责，不以 HTTP 成功冒充可看。
- 修改播放策略执行 core 测试；修改 Android 执行相关单测、Lint 和 APK 构建。
- 不操作用户家庭设备，除非用户明确安排实机测试。
