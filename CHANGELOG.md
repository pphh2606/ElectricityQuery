# 更新日志

## 新增功能
- Android 6.0 及以上设备的系统栏改为透明沉浸式显示。新增 values-v23 / values-night-v23 主题覆盖，并在 MainActivity 中按 API 级别切换 WindowCompat.enableEdgeToEdge，Android 10+ 同时关闭 isNavigationBarContrastEnforced。
- 首页的外部应用打开确认弹窗改为半屏底部弹窗。HomeScreen 为 BottomSheetDialog 显式传入 fullscreen = false，使 ModalBottomSheet 不再占据整屏，保留页面上下文。

## Bug 修复
- 修复底部弹窗关闭时关闭回调被重复触发的问题。BottomSheetDialog 的隐藏动画结束后只复位 isHiding，不再自动补调 onDismissRequest，避免返回键、scrim 点击和程序化关闭路径重复执行回调。

## 架构改进
- WebView 的创建、加载、进度、错误和释放逻辑抽成可复用组件。新增 WebViewHost 与 WebViewHostState，统一封装 WebViewClient、WebChromeClient、文件选择、下载监听和 onRelease 清理，WebViewBottomSheet 与 PaymentOverlay 共用同一实现。
- WebView 半屏弹窗和支付半屏弹窗统一改用通用底部弹窗实现。两个页面从手动 AndroidView、自定义动画和 scrim 迁移到 BottomSheetDialog + ModalBottomSheet，并利用 contentModifier、contentPadding、onHideStarted 等新参数控制布局与关闭动画。
- 支付确认页的金额、选择支付方式、等待支付、支付成功和错误提示拆成独立组件。PaymentConfirmScreen 按阶段抽取 private composable，并新增共享的 PaymentPrimaryButton 与 PaymentMethodCard，减少重复 UI 代码。
- 通用底部弹窗增加自定义内容布局能力。BottomSheetDialog 新增 contentModifier、contentPadding、contentArrangement 和 onHideStarted 参数，同时将标题、图标和列表项图标逻辑抽成私有组件。

## 代码清理
- 清理多个页面的无用导入和失效引用。DashboardScreen、HallScreen、NoticeScreen 和留言相关页面仅整理 import 顺序并移除未使用依赖。

## 工程配置
- 应用构建版本号由 1540 递增到 1543。
