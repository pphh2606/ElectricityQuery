# 更改日志

## 新增功能
- 登录页面新增「其他登录方式」底部弹窗，整合扫码登录、凭据登录、手机号找回、邮箱找回四种入口，替代原来单一的扫码登录按钮
- 登录页面学号和密码输入框添加 `Icons.Default.Person` / `Icons.Default.Lock` 前置图标，提升视觉辨识度
- 登录页面新增取消按钮，点击可直接返回上一页
- 桌面快捷方式创建失败时区分「系统不支持」和「权限不足」两种场景，分别显示 `shortcut_not_supported` / `shortcut_permission_hint` 提示
- 桌面快捷方式支持应用已在前台运行时通过 `onNewIntent` 响应新的快捷方式启动，不再需要冷启动

## Bug 修复
- 自定义网站弹窗点击输入框后弹窗只上移固定距离（小于键盘高度），导致输入框被输入法部分遮挡，`BottomSheetDialog` 新增键盘可见性检测（`WindowInsets.ime.getBottom`），键盘弹出时自动将半展开的 sheet 展开到全屏状态（`LaunchedEffect` + `sheetState.expand()`）
- `BottomSheetDialog` 的 `contentWindowInsets` 已包含 `systemBars`（含导航栏），但 Column 又额外调用了 `.navigationBarsPadding()`，导致导航栏 padding 被双重计算，移除多余的 `navigationBarsPadding()`
- `AndroidManifest.xml` 的 Activity 缺少 `windowSoftInputMode` 声明，添加 `adjustResize` 确保系统正确处理软键盘弹出时的窗口调整

## 架构改进
- `ProfileScreen` 个人页面功能入口从三个独立的 `ElevatedCard` 重构为单个 `Surface` 容器 + 可复用的 `ProfileEntry` 组件，与 `SettingsScreen` 的 `SettingsEntry` 风格统一
- `ShortcutHelper.createPinnedShortcut` 返回值从 `Boolean` 改为 `CreateResult` 密封类（`Success` / `NotSupported` / `Failed`），调用方可精确区分失败原因
- `MainActivity` 快捷方式状态从 `remember` 改为 `mutableStateOf` + `onNewIntent` 更新，新增 `shortcutLaunchId` 计数器确保 Compose 重组，`AppShell` / `NavGraph` 逐层透传
- `BottomSheetDialog` 新增 `contentWindowInsets = { WindowInsets.systemBars.union(WindowInsets.ime) }` 参数，显式声明 sheet 内容区域的窗口 insets

## 依赖变更
- `VERSION_CODE` 从 1193 升至 1226

## 国际化
- 6 语言新增 `login_other_login` / `login_method_qr_scan` / `login_method_credential` / `login_method_phone_recovery` / `login_method_email_recovery` / `login_method_coming_soon` 字符串（中/英/日/繁中/法/阿拉伯）
- 中英文新增 `shortcut_not_supported` / `shortcut_permission_hint` 字符串

## 文档
- `README.md` 根据项目实际更新：技术栈版本号（OkHttp 5.3、Gson 2.14、Navigation 2.9、CameraX 1.6 等）、目录结构（新增 shortcut/、login/ 扩展、components/ 扩展、util/ 扩展）、新增文件详解、新增国际化章节
