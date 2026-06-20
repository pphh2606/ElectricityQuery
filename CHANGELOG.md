# 更改日志

## 新增功能
- 登录页已保存密码现在显示为占位圆点（●●●●●●●●），眼睛按钮在占位状态下自动禁用，防止误触查看已保存密码，密码输入框使用 visualTransformation 控制显示
- 底部弹窗（BottomSheetDialog）关闭时新增退出动画，不再瞬间消失，所有弹窗页面（安全提示、删除账号、账户管理、语言切换等）已统一迁移
- 费用服务大厅新增独立网络模块（FeeModels + FeeServiceHallApi），数据模型和接口层与主网络层解耦
- 新增 SessionTypes 统一定义会话状态类型，认证流程的类型更清晰

## Bug 修复
- 修复 PaymentWebViewEngine 编译失败问题，补充缺失的 ValueCallback 导入
- 修复登录页切换账号时密码可能残留的隐患，登出时自动清除 hasSavedPassword 状态
- 修复底部弹窗在快速切换显示/隐藏时可能出现的状态不一致，内部使用 isHiding + LaunchedEffect 驱动的动画状态机

## 架构改进
- 网络层从扁平结构重组为按功能域划分的子目录：auth（认证）、common（公共工具）、campusphere（今日校园）、electricity（电费）、qrcode（二维码）、feehall（费用大厅），涉及 16 个文件迁移
- AccountStore 账号密码存储升级为 EncryptedSharedPreferences（AES-256 加密），密码始终加密保存在本地
- LoginViewModel 大幅精简，移除调试诊断日志，登录流程逻辑更清爽
- LoginRepository 中间层去掉，LoginViewModel 改为直接调用 CasAuthApi
- HttpClientProvider 删除，功能合并到 HttpClientFactory 统一管理
- SessionValidator 删除，会话验证能力合并到 SessionManager
- 首页（HomeScreen）布局和状态管理优化
- 个人中心（ProfileScreen）信息加载逻辑优化
- 关于页面（AboutScreen）和个性化设置页面（PersonalizationScreen）UI 调整
- 语言切换组件（LanguageSwitchComponents）交互优化
- 账户管理弹窗（AccountManagerSheet）简化，减少约 30 行代码
- 缴费记录详情页（OrderDetailScreen）和充值页面（RechargeScreen）体验优化
- 反馈页面（FeedbackScreen）、挂失页面（CardLostScreen）、快捷方式页面（AddShortcutScreen）统一适配新版弹窗组件

## 删除的文件
- HttpClientProvider.kt — 功能合并到 HttpClientFactory
- SessionValidator.kt — 逻辑合并到 SessionManager
- LoginRepository.kt — LoginViewModel 直接调用 CasAuthApi
- TabScaffold.kt — 未使用的组件，已清理
- ShortcutHelper.kt — 功能移除
- colors.xml — 移除未使用的颜色定义
- 各语言资源文件中约 120 条未使用的字符串已清理（涵盖中文、英文、法语、日语、阿拉伯语、繁体中文）

## 依赖变更
- VERSION_CODE 从 1253 升至 1277
- 旧 CHANGELOG.md 内容清空，重新编写
