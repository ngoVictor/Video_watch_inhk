# 目录与更新协议

外部目录为 UTF-8 JSON，最大 2 MiB：schemaVersion=1；revision 为正整数（不超过 Java long），同一来源严格递增；channels 为 1..2000 条。仅内置初始化目录允许空 channels，远程空目录拒绝并保留缓存。

频道字段：id（稳定身份）、name（1..80 字）、group（央视/卫视/地方/香港/国际）、可选 region（1..80 字）、sources（0..20 条）。空 sources 表示暂不可看，不算备用。

线路字段：id、url（HTTP/HTTPS，最多 2048 字符，不允许用户信息或片段）、priority（整数 0..100），可选 upstream（1..120 字）、mimeType（空值、application/x-mpegURL、application/vnd.apple.mpegurl、application/dash+xml、video/mp2t、video/mp4）。频道 ID、线路 ID 分别唯一，URL 全目录唯一。ID 为 [A-Za-z0-9._-]{1,100}。名称等文本非空、不可含控制字符。

生产者保留稳定 channelId/sourceId，App 不读取其研究报告或黑名单。schemaVersion 不兼容时拒绝更新。目录协议由生产者维护，本仓库保留消费者契约与合成样例；无需共享源代码或同时发布。内置快照只在构建时由显式输入更新，运行时通过 HTTPS 获取目录。当前按 revision 比较，切换到另一套编号更小的目录需清除应用目录缓存/应用数据；同一生产者不得重置 revision。

私用 catalog-config.json 仅含 catalogUrl（HTTPS 目录地址）。频道 token 只发送到绑定的精确 GitHub contents URI；不发送给媒体、更新清单或重定向目标。

独立 APK 清单：schemaVersion=1、versionCode、versionName、minSdk、apkUrl（HTTPS）、sha256、notes。发布清单最后更新，APK 验证大小、散列、包名、版本和原签名后由系统安装。
