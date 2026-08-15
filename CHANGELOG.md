## 新增功能
- 下载源列表会先检测各镜像的连通性和延迟，用户在选择前就能看到可用状态。`UpdateFoundSheet` 通过新增的 `UpdateDownloadProbe` 并发发送 HTTP Range 请求并采样 256KB 数据，逐项展示延迟毫秒数或连接失败提示。
- 更新弹窗新增“不再提示此版本”开关，自动检查更新时会记住被跳过的版本号。`UpdateCheckCoordinator.check(respectSkipped = true)` 依据持久化的 `SKIPPED_UPDATE_VERSION` 过滤已跳过版本，手动检查仍可查看并取消跳过。
- 下载源扩充为 GitHub Raw、gh-proxy.org、fastgit.cc、ghfast.top、gh.chjina.com、github.boki.moe 六个镜像，替代原有 jsDelivr 链接，提高国内环境下获取 APK 的成功率。
## Bug 修复
- 更新说明较长时弹窗内容可独立滚动，不再被底部按钮遮挡。`BottomSheetDialog` 在启用 `bottomBar` 后拆分为固定标题、`weight(1f)` 滚动内容区和底部操作栏。
- 弹窗默认模糊强度由 20 调低到 10，避免更新弹窗打开时背景过度虚化影响正文辨识。`SettingsKeys.SHEET_BLUR_RADIUS` 的默认值同步调整。
## 架构改进
- 镜像地址统一收敛到新增的 `UpdateMirrorSources`，更新元数据和 APK 下载链接共用同一套来源配置。`UpdateDownloadLinks.create()` 与 `UpdateRepository.endpointUrls()` 改为委托该组件生成地址，消除两处硬编码。
- 通用弹窗组件增加 `fixedHeader` 与 `bottomBar` 支持，更新弹窗改为固定标题、滚动内容和底部操作区布局。`BottomSheetDialog` 同时按全屏状态区分系统栏与导航栏边距。
## 工程配置
- CI 生成的更新说明中，APK 链接从 jsDelivr CDN 改为 GitHub 官方 blob 地址。`.github/workflows/build.yml` 同步更新 CI 包和稳定包的 `--link` 参数。
## 本地化资源
- 为“不再提示此版本”、下载延迟和连接失败等新文案补齐。
- 移除不再使用的 `update_download_source_github_raw`、`update_download_source_original`、`update_download_source_ghproxy`、`update_download_source_fastgit` 文案。
