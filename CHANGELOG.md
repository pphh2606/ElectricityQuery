# 更改日志

## 新增功能
- 用户无法从个人页面快速启动常用功能，新增桌面快捷方式入口（ProfileScreen 添加 `AddToHomeScreen` 图标卡片），支持选择首页功能并自定义名称后通过 `ShortcutHelper` + `ShortcutManagerCompat` 创建 Pinned Shortcut，启动时自动导航到对应页面
- 关于页面缺少构建溯源信息，新增构建信息条目（`BuildConfig.BUILD_TIME` / `BuildConfig.GIT_COMMIT_HASH`），点击可跳转 GitHub 对应 commit 页面，`build.gradle.kts` 使用 `ProcessBuilder` 获取 git hash 并兜底 "unknown"
- 桌面快捷方式启动时缺少路由分发逻辑，NavGraph 新增 `LaunchedEffect(shortcutAppInfo)` 根据 `HomeAppIds` 分发到对应路由（二维码/电费/卡务中心/通知/缴费大厅/我的信息），网页类功能通过 `Routes.unifiedWebViewRoute` 打开内置浏览器
- 新增 `AddShortcutScreen` 完整页面：预览区 + 名称输入 + 底部弹窗选择功能列表 + 创建按钮，支持 Coil `AsyncImage` 异步加载图标
- 新增 `ShortcutHelper` 工具类：封装 `createPinnedShortcut`、`extractShortcutAppInfo`、`loadIconFromUrl`（Coil 下载 + `Bitmap.copy` 防缓存回收）
- 新增中英文双语 `strings_shortcut.xml` 资源文件（12 条字符串），支持快捷方式功能的完整国际化

## Bug 修复
- 会话验证在弱网或 HTTPS 重定向场景下频繁失败，`SessionValidator` 的 `readTimeout`/`writeTimeout` 从 5s 增大到 15s，`followRedirects`/`followSslRedirects` 从 false 改为 true 允许自动跟随重定向
- 智能切换账号时可能出现 Cookie 串号（返回的学号与目标不一致），`LoginViewModel` 新增 `result.info.username != username` 校验，不匹配时拒绝自动切换并输出 warn 日志
- 关于页面联系方式弹窗点击任一选项后弹窗未自动关闭，`AboutScreen` 中 QQ/Bilibili/Email 三个 `onClick` 回调添加 `showContactSheet = false`

## 架构改进
- `BottomSheetDialog` 左右按钮从 `wrapContentSize` 改为 `weight(1f)` 等分空间，拖拽手柄改为固定宽度自然居中，解决手柄偏移问题；新增 `skipPartiallyExpanded: Boolean?` 可选参数支持显式覆盖
- `LoadingDialog` 从裸 `Column` 升级为 `Card` 包裹（`RoundedCornerShape(16.dp)` + 8dp 阴影），视觉更统一
- `FeedbackScreen` 标题和内容输入框添加 `RoundedCornerShape(12.dp)` 圆角，与整体风格一致
- `BillScreen` 筛选面板 `FilterPanel` 添加 `horizontal = 16.dp` 水平内边距，避免内容贴边
- `QrLoginScreen` 二维码显示区域从 240dp 增大到 320dp，提升扫码识别率
- `MainTabScreen` / `AppShell` / `NavGraph` / `MainActivity` 逐层传递 `shortcutAppInfo` 参数，保持组件职责单一

## 依赖变更
- `VERSION_CODE` 从 1103 升至 1193
- `build.gradle.kts` 新增 `java.time.LocalDateTime` / `DateTimeFormatter` 导入用于构建时间戳

## 国际化
- 6 语言新增 `about_build_info` 字符串：中文"构建信息" / 英文"Build Info" / 日文"ビルド情報" / 繁中"構建資訊" / 法语"Infos de build" / 阿拉伯语"معلومات البناء"
