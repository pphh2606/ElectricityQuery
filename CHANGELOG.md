# 更新日志
## 新增功能
- 个性化设置新增“弹窗背景模糊”开关：开启后底部弹窗背后的页面内容会动态模糊，Android 12 及以上通过 Haze 实现，并配合弹窗开合状态做 300ms 平滑过渡。
- 配置页新增“日志等级”设置：可控制应用日志输出量及意见反馈附带的日志内容，支持全部、调试、信息、警告、错误和关闭六档；调试包默认全部，正式包默认警告。
- 意见反馈日志改为读取应用内日志缓冲：新增 AppLog/AppLogBuffer 统一采集、限长存储和导出崩溃记录，不再依赖 logcat 命令，提升不同 ROM 上的稳定性与可复现性。
- 底部弹窗选择项增加明确的选中态：夜间模式、标题栏颜色、页面过渡、减少动画和日志等级列表以主色调高亮选中项并显示对勾图标，便于识别当前配置。
- 二维码圆角滑块支持 1% 连续调节：从原来的 0/10/20/30/40/50 固定档位改为 0-50 任意整数百分比，滑块以 49 个步进连续取值。
- 关于页会按构建来源显示 CI 构建信息：使用 BuildConfig.BUILD_SOURCE 判断是否为 GitHub Actions 构建，替代原先写死的 false。
- 新增弹窗模糊、日志等级等文案的多语言支持：简体中文、英文、法文、阿拉伯文、日文和繁体中文均已补充 strings_settings 资源。
## Bug 修复
- 修复日志脱敏覆盖不全的问题：LogRedactor 新增 userId、studentId、studentNo、学号、实名等敏感键及 JSON 字段匹配，反馈日志和崩溃堆栈在写入前统一脱敏。
- 修复关于页构建来源固定显示错误的问题：CI 构建识别改为读取 BuildConfig.BUILD_SOURCE，不再硬编码为 false。
## 架构改进
- 统一应用设置状态管理：MainActivity 用 AppSettingsState 收敛夜间模式、纯黑、主题色、页面过渡、减少动画、标题栏、二维码和日志等级等状态，替代原来多个独立 CompositionLocal；所有读写都通过统一接口持久化。
- 重构设置存储层：SettingsPreferences 改为基于泛型 SettingKey 的读写入口，枚举设置拆分为独立文件，保留原有 SharedPreferences 键名以兼容已保存配置。
- 统一日志入口：全模块 android.util.Log 迁移到 AppLog，日志先经 LogRedactor 脱敏再写入 logcat 和应用内缓冲，URL 与响应体也有专用脱敏方法。
- 合并主题色生成逻辑：删除 generateColorSchemeFromSeed 包装，直接调用 material-kolor 的 dynamicColorScheme。
- 统一标题栏颜色读取：各页面通过 currentTopBarColors() 从 AppSettingsState 获取 TopAppBar 配色，替代 LocalTopBarState 的重复读取。
## 依赖变更
- 新增 Haze 1.6.10 依赖：在 gradle/libs.versions.toml 引入 dev.chrisbanes.haze:haze，用于底部弹窗的背景模糊效果。
## 删除的文件
- 删除 app/src/main/java/edu/cqwu/electricity/theme/ui/ThemeColorGenerator.kt：其 generateColorSchemeFromSeed 包装函数不再需要，主题直接调用 material-kolor dynamicColorScheme。
## 工程配置
- 应用版本号从 1523 提升到 1539：app/version.properties 的 VERSION_CODE 更新，构建产物版本随之递增。
## 其他变更
- 二维码默认配色从“跟随主题”改为“黑白单色”：新安装用户首次打开二维码设置时默认选中单色模式，旧用户已保存的主题色设置仍保留。
