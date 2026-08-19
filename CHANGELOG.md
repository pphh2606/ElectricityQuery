## 新增功能
- 主题色选择器从预设色板升级为 HSV 滑杆加 HEX 输入，并可实时预览，改为底部弹窗呈现。`PersonalizationScreen` 通过新增的 `ColorPickerUtils` 完成 HSV/HEX 转换与校验，用户可更精细地定制主题色。
- 纯黑背景、弹窗模糊、二维码亮屏以及通用设置开关的整行区域都可以点击切换，不用精确点到开关。`SettingsSwitchEntry`、`QrCodeSettingsScreen` 和 `PersonalizationScreen` 为相关行补充 `Modifier.clickable`，扩大可点击区域并统一触发对应的设置更新方法。
- 颜色选择器补齐多语言文案。各 `values*/strings_settings.xml` 新增色相、饱和度、明度、HEX 输入及错误提示等 `personalization_choose_color_*` 资源。
## Bug 修复
- 打开链接对话框在键盘弹出或小屏幕下不再被截断。`OpenUrlDialog` 将地址输入框与内网、半屏选项放入 `verticalScroll` 的可滚动列中，避免内容溢出弹窗。
## 架构改进
- 更新下载源逻辑统一收敛到 `UpdateMirrorSources`，下载链接由原始链接提取文件名后直接生成。移除 `UpdateDownloadLinks` 包装层，`UpdateDownloadLink` 数据类与 `downloadLinks()` 一并内聚到镜像源模块。
- 更新下载探测改为只读取 16KB 数据，仅保留可用性与延迟结果。`UpdateDownloadProbe` 去掉 256KB 采样和速度、错误字段，并复用共享的 `updateHttpClient`，降低探测耗时与内存开销。
- 更新检测的版本比较统一收敛到结果转换处。`toUpdateCheckResult` 直接与 `BuildConfig.VERSION_CODE` 比较并返回 `Found/NoUpdate`，移除 `UpdateRepository.needsUpdate()`，职责更清晰。
- 更新请求的响应解析与超时处理更稳健。`fetchInfo()` 在 OkHttp 回调内集中捕获 JSON 解析异常，`check()` 改用 `withTimeoutOrNull` 并安全恢复 continuation，超时后取消剩余请求。
## 删除的文件
- 删除 `UpdateDownloadLinks.kt`，其下载链接生成职责已由 `UpdateMirrorSources` 承担。

