# 更改日志

## 新增功能
- 打开网址弹窗新增「半屏访问」选项，用户可选择在半屏 `WebViewBottomSheet` 中浏览网页，无需跳转全屏页面
- 二维码页面底部新增「充值」和「订单记录」快捷按钮，扫码后可快速跳转操作
- 二维码页面和扫码页面支持长按复制二维码内容（`SelectionContainer`），方便用户提取链接
- 二维码页面布局优化：扫描提示移至顶部，余额移至二维码下方并使用固定占位避免加载前后 UI 移位
- 扫码页面二维码上方新增扫描提示和过期时间文字，下方展示可复制的链接内容
- 存储清理页面新增下拉刷新功能（`PullToRefreshBox`），用户可随时重新计算各项存储占用大小

## Bug 修复
- `WebViewBottomSheet` 关闭动画结束后不再遗漏调用 `onDismissRequest()`，修复了关闭后遮罩层（Scrim）残留不消失的问题
- `OpenUrlDialog` 从 `AlertDialog` 改为始终组合的 `BottomSheetDialog`，配合 `visible` 参数控制显隐，避免条件组合导致的状态丢失
- 关于页面的 CI 构建标识从硬编码 `false` 改为读取 `BuildConfig.BUILD_SOURCE`，CI 构建的 APK 能正确显示构建来源

## 架构改进
- `OpenUrlDialog` 从 `AlertDialog` 迁移为 `BottomSheetDialog`（MD3 ModalBottomSheet），`Checkbox` 替换为 `Switch`，交互更符合 Material 3 规范
- 半屏 WebView 状态提升到 `MainTabScreen` 层级，避免被 `HorizontalPager` 裁剪，`ProfilePageContent` 通过 `onOpenHalfScreen` 回调通知父组件
- 扫码页面「其他应用打开」改为「分享网址」（`Intent.ACTION_SEND`），语义更准确且支持系统分享面板
- 存储清理页面将计算大小逻辑抽取为 `reloadSizes()` 挂起函数，首次加载和下拉刷新复用同一方法

## 国际化
- 全部 6 种语言新增 `open_url_half_screen`（半屏访问）字符串
- 全部 6 种语言新增 `qrcode_display_orders`（订单记录）字符串
- 全部 6 种语言将 `scan_open_external` 从「其他应用打开」更新为「分享网址」

## 依赖变更
- VERSION_CODE 从 1369 升至 1398
