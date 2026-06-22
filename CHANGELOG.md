# 更改日志

## 新增功能
- 登录页新增「找回密码」独立入口（底部栏显示为"其他登录方式 | 找回密码"），找回密码弹窗与扫码/凭据登录分离，避免用户混淆
- 新增 DatePickerField 公共日期选择组件，从 BillScreen 私有实现提取为可复用组件，账单筛选和费用服务大厅订单筛选统一使用，点击输入框弹出 Material3 DatePickerDialog 选择日期
- 登录安全说明（SecurityNoticeSheet）从 5 条扩充为 7 条，新增找回密码功能说明和安全下载渠道提示，覆盖所有登录方式的安全承诺
- WebView 错误叠加层新增 SSL/证书错误（ERR_SSL / ERR_CERT）和连接重置错误（ERR_CONNECTION_RESET / ERR_CONNECTION_CLOSED）的识别与提示
- 充值记录导出文本中"充值人"字段改为"充值人名"，语义更清晰

## Bug 修复
- WebView 错误叠加层的网络错误判断从硬编码数字错误码（如 -2、-8、-15）改为描述字符串匹配（如 ERR_INTERNET_DISCONNECTED、ERR_TIMED_OUT、ERR_NAME_NOT_RESOLVED），解决了部分设备/WebView 版本错误码不一致导致错误类型误判的问题
- 修复扫码页面（ScanScreen）CameraX ProcessCameraProvider.getInstance() 在主线程阻塞的问题，改为 addListener 异步回调方式获取 provider，避免首次打开扫码页卡顿

## 架构改进
- 全局 Material Icons 从 Filled（实心）统一迁移到 Outlined（线框）风格，涉及约 40+ 个 UI 文件；返回箭头统一使用 Rounded 风格（Icons.AutoMirrored.Rounded.ArrowBack），首页扫码图标从 PhotoCamera 改为 CenterFocusWeak
- AccountStore 重构为单例模式（Double-Checked Locking + @Volatile），Application.onCreate 中预初始化 EncryptedSharedPreferences，避免首次调用时 ~100ms 初始化阻塞 UI 组合线程导致滑动动画卡顿；所有调用点从 AccountStore(context) 迁移到 AccountStore.getInstance(context)
- 登录页"其他登录方式"弹窗精简，仅保留扫码登录和凭据登录两项；手机号找回和邮箱找回移至独立的"找回密码"弹窗
- 账单页面（BillScreen）移除私有 DatePickerField 实现（约 60 行），改为导入公共组件 DatePickerField，减少重复代码
- 个人中心（ProfileScreen）和大厅页面（HallScreen）增加性能诊断日志（TabPerf），追踪 Tab 切换时的 Compose 组合耗时
- 充值记录导出文本格式微调：标点符号规范化（冒号后统一有空格）

## 新增的文件
- DatePickerField.kt — 公共日期选择器组件（Material3 DatePickerDialog + OutlinedTextField），支持日期格式化和回填

## 依赖变更
- VERSION_CODE 从 1277 升至 1300
