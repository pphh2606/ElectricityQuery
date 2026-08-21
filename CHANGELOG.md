## 新增功能
- 首页新增“学生绑定银行卡”入口，用户可在原生页面选择银行、输入卡号绑定或解绑。新增 `BankCardBindApi`、`BankCardBindViewModel`、`BankCardBindScreen`，API 复用 CAS/EPay 会话并解析银行列表、`checkexist` JSON 与绑定结果 HTML，界面用 `StateFlow` 管理加载、提交、登录过期等状态。
- 设置新增“网页深色模式”开关，内置网页会跟随应用夜间设置显示深色。新增 `WebViewDarkMode` 工具，Android 10+ 使用 `setForceDark`/`setAlgorithmicDarkeningAllowed`，旧版本注入深色 CSS，并在页面加载及设置变化时应用到整页与底部弹窗 WebView。
- WebView 停在 CAS 登录页时改为显示“重新登录”遮罩，点击后再进入本地登录，返回后自动刷新页面。`UnifiedWebViewScreen` 和 `WebViewBottomSheet` 使用可消费触摸的 `ReLoginContent(consumeTouches = true)` 覆盖层，登录刷新信号通过 `LocalWebViewReloadAfterLogin` 跨页面传递。
- 存储清理页面会提示清理后自动重启，并在清除需要重启的数据后自动重启应用。`StorageClearScreen` 新增 `restartApp()`，以 `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK` 启动 `MainActivity` 后调用 `exitProcess(0)`。
- 下拉菜单圆角统一为 12dp。`AppScaledDropdownMenu` 默认 `shape` 改为 `RoundedCornerShape(12.dp)`，与应用内弹窗风格保持一致。
## Bug 修复
- 修复登录页在加载中按返回键被拦截的问题，系统返回键现在可直接退出登录页。移除 `LoginScreen` 中的 `BackHandler`，避免登录请求期间误触返回造成状态丢失或返回栈异常。
- 修复部分设备无法打开 WebView 网络设置的问题，网络设置入口统一使用兼容性更广的 `Settings.ACTION_WIRELESS_SETTINGS`。
- 修复通知详情在深色模式下的排版与改色问题，改为复用统一的 WebView 深色模式。`NoticeDetailScreen` 移除硬编码内联 CSS 与 `!important` 覆盖，补齐 `<meta charset="utf-8">`，避免服务端内容样式被强制改写。
- 修复底部弹窗 WebView 在本地登录成功后不刷新的问题。`WebViewBottomSheet` 现在消费 `LocalWebViewReloadAfterLogin` 并调用 `webViewState.reload()` 刷新当前页面。
## 架构改进
- 夜间模式判断统一为扩展函数。新增 `NightMode.isDark(systemDark)`，`Theme`、`NoticeDetailScreen` 等页面复用该函数解析 SYSTEM/LIGHT/DARK 的最终深浅色，消除重复判断逻辑。
- WebView 登录刷新状态提升为导航级共享状态。`NavGraph` 用 `CompositionLocalProvider` 提供 `LocalWebViewReloadAfterLogin` 与 `LocalWebViewReloadConsumed`，整页 WebView 与底部弹窗 WebView 共用同一套“登录后刷新”协议。
- 通知详情 HTML 改为无自定义样式的纯内容页。`buildHtmlPage()` 只负责正文与字符集，深浅色统一交给 `WebViewDarkMode` 处理，降低内容展示与主题的耦合。
- 登录过期识别从自动跳转改为状态判断。`WebViewUrlUtil.shouldShowLoginRequired()` 只判断“最终停留 CAS 登录页且无主框架错误”，避免断网、DNS 等错误被误判为未登录。
## 资源清理
- 清理不再使用的多语言资源。`strings.xml`、`strings_cardcenter.xml`、`strings_feedback.xml`、`strings_speakup.xml` 中旧的通用重试、账单结构提示、旧登录提示、反馈日志与留言错误文案在各语言目录同步移除。
- 新增学生绑卡与网页深色模式的多语言文案。`strings_cardcenter.xml` 补齐 `bank_card_*` 资源，`strings_settings.xml` 新增 `personalization_webview_dark_mode*` 和存储清理重启提示。
