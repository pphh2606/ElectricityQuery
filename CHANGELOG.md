## Bug 修复
1. 修复校内网络测速页在 Android 7 及以下设备上一打开就崩溃的问题：时间戳格式化按系统版本分流，java.time 只在 API 26 以上加载，低版本走 SimpleDateFormat 回退
2. 修复登录会话在低版本 Android 上可能异常的问题：域会话表改用 API 1 即可用的 get 加 putIfAbsent，替换需要 API 24 的 computeIfAbsent
3. 修复英语与法语下人数量与登录记录条数文案在单数或大数量时表达不准确的问题：补齐 one 与 many 复数类别

## 架构改进
1. 清理无引用的成员与参数：删除 AccountSessionStore.getActiveStudentId、SessionCoordinatorV2.importAccounts、CredentialBackupViewModel.currentUsername、SpeedTestViewModel.isBusy 与 LoginLogApi 的 LOG_URL 常量等
2. 数值型可变状态改用原始类型状态：界面缩放、二维码圆角、调色板色相与弹窗模糊进度改用 mutableFloatStateOf 与 mutableIntStateOf，避免自动装箱开销
3. 系统 API 调用改用属性写法并补编译期常量：WebSettings 的各类 setter 改为属性赋值，WebView 夜间模式注入脚本改为 const val
4. 协程相关修饰调整：协程活跃判断改用 currentCoroutineContext，pingOnce、failWith 与 doFetchStudentInfo 去掉多余的 suspend 修饰
5. 统一各文件的 import 顺序并删除未使用 import：覆盖电费、缴费大厅、登录、公告、设置与我的信息等十余个文件
