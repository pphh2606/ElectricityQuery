# 更新日志

## 兼容性改进
- 应用最低支持版本从 Android 6.0 降为 Android 5.0（`minSdk` 从 23 改为 21），让更多旧手机也能安装运行。

## Bug 修复
- 修复 WebVPN 自动登录后 Cookie 未同步的问题。
- 补齐旧版 Android 的兼容处理（自动登录完成后把 clientvpn 域的 Cookie 写回当前账号存储；Android 10 以下不再把二维码写入系统相册、Android 6 以下改用旧锁屏接口并统一弹窗拖拽行为、WebView 错误回调在 API 23 以下读取不到错误对象时使用默认文案）。

## 安全改进
- 新增日志脱敏工具 `LogRedactor`，并在登录、扫码、支付、WebVPN、通知、办事大厅等日志中统一隐藏 Cookie、Token、密码、学号、姓名和重定向地址等敏感信息（调试日志里用 `****` 替代真实值，降低 Logcat 泄露个人信息的风险）。

## 架构改进
- 调整 WebVPN 网络链路：二维码登录请求不再走 WebVPN 转换。
- 图片加载改用独立的 WebVPN 图片客户端（会话过期时返回统一占位响应而不是把异常抛到 OkHttp 异步线程）。
- 重定向跟踪增加 Referer 支持，让不同业务使用更匹配的网络通道。

## 界面优化
- 语言选择弹窗改为可滚动列表，并允许弹窗停留在中间展开高度（`BottomSheet` 部分展开），语言选项较多时也不会被截断或遮挡。

## 依赖变更
- 下调 `coreKtx`、`lifecycle-runtime-ktx`、`activity-compose`、Compose BOM、`materialKolor`、`CameraX` 等依赖版本，以兼容 Android 5.0 并保持既有功能可用（这些库的新版本要求更高系统版本）。

## 工程配置
- 应用版本号从 1478 提升到 1491。