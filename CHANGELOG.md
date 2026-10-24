## 新增功能
1. 个性化页新增主题风格与取色规范，可从九种调色板里挑一套，并在 Material 3 2021 与 Expressive 2025 两种规范间切换；对应 material-kolor 的 PaletteStyle 与 ColorSpec.SpecVersion，默认使用 2025 版
2. 个性化页新增界面风格，可在 Material 3 与 MIUI 之间切换；MIUI 风格由 miuix 的 ThemeController 用同一颗种子色生成配色，再映射进 Material 3 的颜色角色
3. 个性化页新增 MIUI 组件预览入口，集中展示 miuix 的卡片、按钮、开关与输入框；页面单独套一层 MiuixTheme，与当前风格互不影响
4. 换主题色、换风格或换规范时整套配色平滑过渡；ColorScheme 的每个颜色角色分别走 spring 动画，开启「减少动画」时直接切换

## 界面与交互优化
1. 大部分页面的标题栏改成大标题随滚动折叠；统一由 CollapsibleScaffold 通过 LargeFlexibleTopAppBar 与 exitUntilCollapsedScrollBehavior 实现
2. 编辑页的保存按钮改为右下角浮动按钮；AppSaveFab 用 SmallExtendedFloatingActionButton 配 CircleShape 与 primary 填充，替代原来的底部占位行
3. 设置分组与底部弹窗的选项行支持按压形变，弹窗选项改为裸图标和更矮的行高；按压时按进度插值分段圆角，行间保留 2dp 缝隙，弹窗行最小高度 56dp
4. 页面级加载指示换成 Expressive 波浪样式，行内小转圈保留普通版；环形与线性分别使用 CircularWavyProgressIndicator 与 LinearWavyProgressIndicator
5. 修改密码页的保存与忘记密码收成官方分段按钮；SplitButtonLayout 配 SplitButtonDefaults 的主次按钮，忘记密码进右侧箭头菜单，整体右下角浮动
6. 课表的周与学期切换改用 Expressive 分段按钮组；ButtonGroup 的 toggleableItem 取代原来的 SingleChoiceSegmentedButtonRow
7. 「我的」页与缴费大厅个人页改为设置页那种分组卡片，个人信息卡恢复圆角线框并统一底色；功能入口共用 SettingsGroup，按压时有形状反馈
8. 全应用启用 Expressive 主题与动效；MaterialExpressiveTheme 默认走 expressive 弹簧曲线，减少动画时退回 standard，切换界面风格不重建页面

## Bug 修复
1. 修复部分设置页底部贴住系统手势条、以及左右手势 inset 把内容挤窄的问题；只为需要的页面补固定底部留白，避让只取 Bottom 方向
2. 修复二维码设置页留白缺失与圆角开关行点击无反馈的问题；圆角开关改 SettingsSwitchEntry，浅色下二维码底色改 surfaceContainer
3. 修复纯黑主题下卡片与页面分不清层级的问题；补 surfaceBright 与 surfaceDim 两档，卡片改用比页面更亮的灰
4. 修复账号管理页时间格式不跟随应用语言的问题；SimpleDateFormat 改用当前 Locale 配置里的 Locale
5. 修复圆角滑块拖动时的废弃 API 警告与 miuix tertiary 配色错配；滑块改用 SliderState 重载，tertiary 用 tertiaryContainer 近似

## 架构改进
1. 主题取色统一走 material-kolor 一套引擎，系统动态色与自定义色共用同一套推导；系统动态色只取 system_accent1_500 当种子
2. 设置页的卡片、行、开关统一到 SettingsGroup 与 SettingsListItem；分组形状由 SegmentedGroup 注入，首尾 16dp、中间 5dp 连接角
3. 设置分组与底部弹窗共用一套分段形状计算；SegmentedGroup 提供 SegmentedGroupScope 与 LocalSegmentedShape，弹窗的 SheetItemGroup 直接复用
4. 圆角统一改用主题形状令牌，不再散落固定 dp；约六十处 RoundedCornerShape 改为 MaterialTheme.shapes 的 large、small 等档位
5. 按钮与图标按钮统一接入 Expressive 形状；统一传入 ButtonDefaults.shapes 与 IconButtonDefaults.shapes，并移除各自的圆角覆盖
6. 页面外壳、外部链接打开与下拉菜单收成公共组件；CollapsibleScaffold、openUrl 与 AppScaledMenuItem 分别替代各页重复的标题栏、try-catch 与菜单写法
7. 明暗判断与排版按 Expressive 调整；LocalAppDarkTheme 替代亮度推算，titleLarge 提到 24sp 并新增 26sp 的 headlineSmall
8. 兼容 material3 1.5 的最低系统要求，应用仍支持 Android 6.0；Manifest 用 tools:overrideLibrary 覆盖库级 minSdk 24 校验，链接解析统一用 toUri
9. 多语言文案同步补齐与清理；六套语言新增界面风格、主题风格与取色规范文案，删除重复占位符

## 依赖变更
1. Kotlin 由 2.3.21 升级到 2.4.21；Kotlin 编译器与 Compose 编译器插件一起升级
2. Compose BOM 由 2026.06.00 升级到 2026.09.00；material3 显式锁到 1.5.0-beta02
3. material-kolor 由 3.0.1 升级到 6.0.0，坐标改为 material-kolor-material3；新增 miuix-ui 0.9.3
4. AndroidX Core KTX、AppCompat、Lifecycle、Activity Compose 与 CameraX 跟随升级；OkHttp 升到 5.5.1，测试用 org.json 改由版本目录管理

## 删除的文件
1. 删掉 values-v23/themes.xml 和 values-night-v23/themes.xml；与基础 values/themes.xml 内容重复，v23 限定已无必要
