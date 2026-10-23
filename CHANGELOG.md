## 新增功能
1. 设置新增标题栏模糊开关，默认开启，控制所有页面标题栏与顶部浮层的毛玻璃；状态存入 header_blur_enabled，系统不支持时自动退化为普通标题栏
2. 新增高级模糊设置页，可调模糊半径、磨砂颗粒、不透明度、底色与性能档位并恢复默认；参数 1:1 映射 HazeBlurStyle 与 hazeBlur 的 blurRadius、noiseFactor、alpha、backgroundColor、performanceMode
3. 弹窗背景模糊新增参数面板，同样可调上述参数；由 sheet_blur_radius、sheet_blur_noise、sheet_blur_alpha、sheet_blur_performance 持久化，AppShell 按弹窗进度缩放半径
4. 首页、办事大厅、缴费大厅的分类索引条、统计行与筛选行移入标题栏浮层并一起模糊；列表状态提升到页面级，高亮按标题栏下沿第一个可见项计算，跳转用负偏移对齐
5. 课表的周次学期切换行、日期表头与节次列改为浮层，滚动时保持可见；TimetableGrid 改为共享横向与纵向 scrollState 的三层叠构
6. 设置条目支持整行进二级设置、右侧开关独立操作；SettingsSwitchEntry 新增 enabled、onRowClick、rowClickEnabled 参数与操作区分隔线

## Bug 修复
1. 弹窗关掉后马上再开不再丢弹出动画，也不易被滑动误关；打开时用 visible 上升沿递增 openGeneration 重建 SheetState，弹出完成前禁用拖拽，关闭时无条件 hide
2. 首页与快捷方式页在应用横跨多个分类时不再崩溃；前者按 appId 去重，后者把 key 改为 categoryId 与 appId 组合
3. 备份与恢复页图标与实际动作一致；剪贴板固定用 ContentCopy，本地文件导出与导入分别用 FileDownload、FolderOpen

## 界面与交互优化
1. 个性化页的模糊开关改为整行可点进入参数面板，系统不支持时副标题说明原因并禁用入口；入口可用性由 rowClickEnabled 与 enabled 分开控制
2. 移除了原来的模糊程度滑块，统一下放到弹窗参数面板；标题栏配色在模糊关闭时仍生效，下拉刷新指示器统一下移到浮层下方

## 架构改进
1. 全项目 40 余个页面改用 BlurScaffold，内容铺满整屏、标题栏作浮层叠在其上；内容层用 hazeSource、浮层用 hazeBlur，按浮层实测高度给首项留白而不是用 contentPadding
2. 毛玻璃开关与参数收敛到公共 API，关闭时不挂 hazeSource 以免每帧录制；记录表格拆出表头、数据行、总计三个组件，表头可单独入浮层并共享列定义
3. 性能档位做成可枚举的 BlurPerformanceMode，参数行抽成共用控件；弹窗模糊的 noiseFactor 与 alpha 改由设置驱动，不再固定为 0

## 依赖变更
1. Haze 由 1.6.10 升级到 2.0.1 并新增 haze-blur 模块；改用 hazeBlur、HazeBlurStyle、HazeInput 与 HazeBlurDefaults，替换已移除的 hazeEffect、HazeStyle 与 HazeDefaults
2. Compose BOM 由 2025.11.01 升级到 2026.06.00

## 构建变更
1. 最低支持系统提升到 Android 6.0，即 minSdk 由 21 提升到 23
2. 编译目标提升到 API 37，compileSdk 改为 release 37 且 minorApiLevel 为 0
