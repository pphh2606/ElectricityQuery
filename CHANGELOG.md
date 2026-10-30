## 新增功能
1. MIUI 风格下可开关 MIUIx 取色，默认用 MIUI 固定的蓝色，开启后跟随主题色；只替换 primary、secondary、error 这类语义色，灰底白卡层级仍取 miuix 原值

## 界面与交互优化
1. 全项目标题栏、底栏、搜索栏与页签栏统一成一套组件，MIUI 下自动换成 miuix 实现；组件各自读 LocalUiStyle 二选一，页面只声明标题与动作
2. 大量二级页面改为直接套页面外壳，不再各写标题栏与模糊层；折叠行为与顶部留白统一由 CollapsibleScaffold 负责
3. 设置页在 MIUI 下变成整张白卡、行与行紧贴、不再显示装饰图标；SettingsGroup 走 MiuixCard，SettingsListItem 走 BasicComponent
4. 加载环、开关、提示条与下拉刷新在 MIUI 下换成 miuix 实现；分别对应 InfiniteProgressIndicator、MiuixSwitch、MiuixSurface 与 miuix 的 PullToRefresh，默认英文提示改中文四段
5. 二维码页把加载中与加载失败收进同一容器，并去掉标题旁的 Cookie 圆点；QrCodeDisplayScreen 不再单独包加载态，会话失效改用整页提示
6. 扫码页顶栏改黑底白字，内置浏览器页改紧凑单行标题并显示域名；AppTopBar 支持指定底色与前景色，浏览器页首帧就用入口 URL 的 host 做副标题

## Bug 修复
1. 修复 MIUI 风格夜间标题栏图标几乎看不见；新增 MiuixTopBarForeground 提供 LocalContentColor，未写 tint 的图标跟随顶栏前景色
2. 修复 MIUI 风格卡片比页面暗、整体发灰；背景与容器层级按语义重排成灰底加白卡，surfaceTint 置透明关掉色调染色
3. 修复 MIUI 风格按压反馈变成水波纹；MiuixTheme 移到 M3 内层，避免 LocalIndication 被 ripple 覆盖
4. 修复下拉刷新时大标题召不回来；新增 ScrollLinkedBox 把滚动连接挂进下拉刷新内部，位移先到标题栏
5. 修复网页页面开启毛玻璃后闪烁甚至不绘制；BlurScaffold 新增 blur 开关，WebView 页关掉录源并回退不透明底色
6. 修复标题栏字重与图标颜色不统一；去掉手写的 FontWeight.Bold 与 actions 里写死的 tint，交回主题样式

## 架构改进
1. 界面风格收成唯一入口；新增 LocalUiStyle 这个 CompositionLocal，由主题层统一 provide，组件层不再各自判断
2. 页面外壳与下拉刷新抽成公共容器；CollapsibleScaffold 支持大小标题与副标题，AppPullToRefresh 统一两种刷新实现
3. BlurScaffold 与 SegmentedGroup 新增可选参数；分别支持指定容器底色与行间距

## 删除的文件
1. 删掉 BlurPullRefresh.kt；只剩几行指示器偏移逻辑，已并入 AppPullToRefresh
