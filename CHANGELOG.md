## 新增功能
1. 教务首页九宫格的「更多服务」改为 App 内原生二级页，不再跳到网页：新增 JwxtMoreServiceScreen 与 JwxtMoreServiceViewModel，注册 Routes.JWXT_MORE_SERVICE 路由并在 jwxtGraph 中登记
2. 更多服务页按分组、分类、服务三级展示并支持下拉刷新：新增 listLabelServiceSet 接口与 LabelServiceSetResponse、JwxtServiceGroup、JwxtServiceCategory 模型，页面观感对齐缴费服务大厅，服务条目点击仍交给内置浏览器，空状态与标题补齐中英文文案资源

## 界面优化
1. 教务首页今日课程与考试卡片的文字可长按选取复制：当前页内容包进 SelectionContainer，透明占位的另一页不参与选择，避免选到隐藏页的文字

## 架构改进
1. 全局导航图改为装配层加各模块自注册：NavGraph.kt 从 420 行精简到 62 行，路由常量集中到 common/navigation/Routes.kt，页面转场动画抽到 app/NavTransitions.kt 的 animatedComposable，16 个模块各自新增 XxxNavigation.kt 提供 xxxGraph 函数，新增页面不再改动全局导航图
2. 设置基元与通用工具上移到 common 包，消除共享层对 app 与业务模块的反向依赖：settings/data 的十个设置定义、theme/ui 的 AppSettingsState、home/data 的 CustomServiceEntry 统一迁入 common/settings，theme/util 的工具迁入 common/util，SheetVisibilityState 迁入 common/ui，LocalNavController 与 DetailType、QrCodeType 迁入 common/navigation，WebView 登录后刷新标记迁入 webview/ui
3. 浏览器标识提供者改为显式初始化：UserAgentProvider 不再依赖 ElectricityApp.instance 单例，改由 ElectricityApp.onCreate 调用 init(context) 注入上下文
4. 重启应用不再硬编码首页 Activity：restartApp 改用 PackageManager.getLaunchIntentForPackage 取启动 Intent 后清空任务栈重启
5. 教务分类组件解耦为通用数据模型：JwxtLabelChips 的参数由 JwxtLabelTab 换成 JwxtChipLabel，教务首页的分类与更多服务页的分组共用同一组件

## 删除的文件
1. 删除 settings/data 目录下的十个设置定义文件：AppLanguage、NightMode、PageTransition、QrCodeColorMode、ReduceMotion、SettingKey、SettingsPreferences、ThemeColorSource、TopBarStyle、UserAgentProvider 全部迁移到 common/settings
2. 删除 theme 包下的共享代码文件：AppSettingsState 迁到 common/settings，AppRestart、ClipboardUtil、ToastUtils 迁到 common/util
3. 删除 home/data/CustomServiceEntry.kt：自定义服务数据类随设置项一起迁移到 common/settings
