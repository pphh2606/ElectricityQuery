# 更新日志

## 新增功能
- 新增“弹窗背景模糊强度”调节：开启弹窗背景模糊后，可在个性化设置中用滑块把模糊半径从 0dp 调到 40dp，新增 floatSetting 持久化配置并实时传入 HazeStyle 渲染。
- 二维码圆角改为连续调节：二维码设置页的圆角滑块从固定的 0/10/20/30/40/50 档位改为 0-50 连续取值，并用一位小数显示当前百分比。
- 新增弹窗模糊强度文案的多语言支持：简体中文、英文、法文、阿拉伯文、日文和繁体中文均已补充“模糊程度”和“X.Xdp”字符串资源。
- WebView 弹窗在输入法弹出时自动展开：检测 WindowInsets.ime 后自动把弹窗切到展开状态，避免输入框被键盘遮挡。
- WebView 弹窗内容区高度由半屏调整为约 70% 屏高：页面获得更大的浏览区域，同时保留拖拽调整和关闭手势。

## Bug 修复
- 修复 WebView 页面顶部下拉与弹窗拖拽的手势冲突：在 WebView 原生触摸事件中通过 VelocityTracker 和 NestedScrollDispatcher 接管顶部下拉手势，页面位于顶部时下拉会拖动弹窗，页面内滚动时仍交给 WebView 处理。
- 修复 WebView 弹窗开合状态与背景模糊不同步的问题：弹窗改用 ModalBottomSheet 状态驱动，并通过 AppShell 与 SheetVisibilityState 统一维护背景模糊、压暗和返回键流程。

## 架构改进
- WebView 半屏弹窗迁移到 Material3 ModalBottomSheet：移除手写 Box、Animatable 高度动画、pointerInput 拖拽和自定义 Scrim，改用系统弹窗组件、标准 DragHandle 及 systemBars + ime 窗口 insets。
- AppShell 的 Haze 背景模糊改为读取用户设置：不再使用固定 20dp 的 HazeStyle，而是根据弹窗模糊半径和开合进度动态计算模糊量，设置变更后无需重启即生效。
- 设置状态统一管理模糊与圆角参数：AppSettingsState 新增 sheetBlurRadius 状态及更新方法，并集中定义 0-40 与 0-50 的边界常量，所有写入先经过 coerceIn 防越界。
- 设置存储层新增 Float 支持：SettingKey 增加 floatSetting 构造器，通过 SharedPreferences 的 getFloat/putFloat 读写浮点配置，二维码圆角存储类型由 Int 调整为 Float。

## 工程配置
- 应用版本号从 1539 提升到 1540
- 修正构建脚本注释
