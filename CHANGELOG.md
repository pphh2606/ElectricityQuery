## 新增功能
- 应用启动后会自动检查是否有新版本，发现更新时直接弹出升级提示。`AppShell` 在 `LaunchedEffect` 中根据 `SettingsKeys.AUTO_UPDATE_ENABLED` 调用 `UpdateCheckCoordinator.check()`，将 `UpdateCheckResult.Found` 渲染为 `UpdateFoundSheet`。
- 设置页新增“更新设置”区域，可以开关自动检查更新、切换 CI/稳定版更新通道并调整检查超时时间。`ConfigScreen` 新增自动更新、检查 CI 版本和 1 到 10 秒超时滑块三个控件，选项通过 `SettingsKeys.AUTO_UPDATE_ENABLED`、`CHECK_CI_UPDATES`、`UPDATE_TIMEOUT_MS` 持久化。
- 发现新版本后可以在弹窗中选择下载源，包括 GitHub Raw、原始 jsDelivr 链接、gh-proxy 与 fastgit 镜像。`UpdateFoundSheet` 配合 `UpdateDownloadLinks.create()` 生成多个下载地址，打开失败时通过 Snackbar 提示没有可用浏览器。
- 关于页“检查更新”不再要求每次手动选择通道，改为跟随设置页里的通道偏好。`AboutScreen` 改用 `UpdateCheckCoordinator` 统一处理检查，结果以 `Found / NoUpdate / Failed` 状态机驱动界面。
- 关于页的“开发者”行改为打开联系方式弹窗，移除了独立的“联系方式”入口和对应文案。
## 架构改进
- 更新检查改为并发请求全部镜像源，并选取版本号最高的有效元数据。`UpdateRepository.check()` 使用 `coroutineScope`、`async` 与 `awaitAll()` 并行拉取 GitHub raw、jsDelivr、gh-proxy 和 fastgit，`selectLatest()` 按 `versionCode` 选择最新结果，单个镜像超时不再阻塞整个检查。
- 更新检查逻辑集中到 `UpdateCheckCoordinator`，把读取设置、选择通道、拉取元数据和判断是否需要更新收敛为统一的 `UpdateCheckResult`。`UpdateRepository` 支持通过 `timeoutMs` 配置连接、读写超时，并保留 `CancellationException` 的向上传播。
- 新增共享更新弹窗组件 `UpdateFoundSheet`，同时供启动自动检查和关于页手动检查复用，避免两套更新界面重复维护。
- 更新设置新增 `AUTO_UPDATE_ENABLED`、`CHECK_CI_UPDATES`、`UPDATE_TIMEOUT_MS` 三个 `SettingsKeys` 配置项，默认自动更新开启、检查 CI 通道、超时 3 秒。
## 本地化资源
- 为更新设置、自动更新和下载源新增多语言文案，并清理不再使用的旧文案。简体中文、繁体中文、英文、法语、日语和阿拉伯语六套 `strings_settings.xml` 同步新增 `config_update_settings`、`update_settings_*`、`update_download_source_*` 资源，删除 `update_channel_title`、`update_copy_link` 与 `about_contact`。
## 工程配置
- 应用构建版本号自动递增到 1566。`app/version.properties` 的 `VERSION_CODE` 由 1545 更新为 1566。
