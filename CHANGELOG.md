## 新增功能
- 新增「查找人员」模块：在"我的"页面增加入口，可按姓名或学号关键字搜索校内人员，分页浏览搜索结果。通过 `PersonSearchApi` 调用 ehall 通用人员选择组件 `choose_person.do` 接口，复用 `ServiceLoginManager.ensureLogin` 完成 CAS 会话初始化；`PersonSearchViewModel` 以 StateFlow 管理 Idle/Loading/Success/Error 状态，用搜索代数（generation）机制丢弃过期请求，支持滚动到底自动加载下一页与下拉刷新；`PersonSearchScreen` 提供下划线样式搜索条、人员卡片与结果统计行，并在 `NavGraph` 注册 `person_search` 路由。
- 新增「查找人员」界面的多语言文案：新增六套 `strings_person.xml` 资源，覆盖搜索提示、结果统计、分页与空状态等文案。
## 架构改进
- 应用夜间模式改为全局即时生效：启动时与运行中切换时都会通过 `AppCompatDelegate.setDefaultNightMode()` 把夜间模式设置应用到整个 Activity。`MainActivity` 由 `ComponentActivity` 改为 `AppCompatActivity`，在 `onCreate` 中读取 `NIGHT_MODE` 设置先行应用，新增 `NightMode.toAppCompatMode()` 将 SYSTEM/LIGHT/DARK 映射为 `MODE_NIGHT_FOLLOW_SYSTEM`/`MODE_NIGHT_NO`/`MODE_NIGHT_YES`，`updateNightMode()` 切换时同步调用。
- 窗口主题切换为基于 AppCompat 的 DayNight 主题以配合夜间模式联动：四套主题资源（`values`、`values-night`、`values-v23`、`values-night-v23`）中 `Theme.电费查询` 的父主题统一由 `android:Theme.Material.Light/NoActionBar` 换成 `Theme.AppCompat.DayNight.NoActionBar`，使 Activity 层能够响应 `setDefaultNightMode` 引发的深浅色重建。
- 移除「网页深色模式」独立开关：网页深色现在直接由应用夜间模式推导，无需单独开启。删除 `WEBVIEW_DARK_MODE` 设置键、`AppSettingsState.webviewDarkMode` 状态与 `updateWebviewDarkMode()`，`shouldApplyWebViewDarkMode()` 只依据 `nightMode.isDark(systemDark)` 判断，并同步移除设置页开关入口与全部语言的对应文案，简化设置项模型。
- 移除网络请求中硬编码的 User-Agent 头：`HallFavoriteApi`、`HallSearchApi`、`SpeakUpApi` 不再为每个请求手工指定固定的 Android Chrome UA，统一交由 OkHttp 客户端提供默认 UA，减少请求头样板代码与维护成本。
## 依赖变更
- 新增 AndroidX AppCompat 依赖以支持 DayNight 主题与夜间模式联动：版本目录 `gradle/libs.versions.toml` 新增 `appcompat = "1.6.1"` 版本与 `androidx-appcompat` 库条目，`app/build.gradle.kts` 添加 `implementation(libs.androidx.appcompat)`。
