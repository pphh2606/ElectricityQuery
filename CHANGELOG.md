## 界面与交互优化
1. 教务更多服务页的分类筛选挪进标题栏，随标题栏一起模糊、不跟内容滚动。分组胶囊改由 expandedTopContent 承载，与标题栏同属浮层。
2. 记录页返回后不再清空重查，再次进入直接看到上次列表；首页在 miuix 风格下点开搜索即自动聚焦。移除 clearState 调用让状态随 ViewModel 存活，MiuixHomeTopBar 补 FocusRequester。
3. 内置浏览器的域名副标题改由脚手架统一渲染，两种风格都显示双行标题。AppSmallTopBar 增加标题加副标题布局，页面不再自己拼 titleContent。
4. 设置页开关行的分隔线两种风格共用一套结构。SettingsSwitchEntry 把分隔线与开关合到同一个 Row，只在开关控件处按风格分支。

## Bug 修复
1. 二维码页登录过期后不再停在旧二维码上，能立刻看到重登入口；首载与下拉刷新也合成一个状态。重登分支提前于已有二维码判断，isLoading 与 isRefreshing 合并为 isFetching。
2. 缴费大厅订单列表的加载更多不再一直沿用第一页的数据。derivedStateOf 的 remember 补上分页字段作为键，字段变化时重建判断。
3. 教务课表筛选弹窗能正常播放关闭动画，打开课表失败也不会卡在打开中。弹窗 visible 由 picker 驱动而非恒为 true，取值协程用 try 与 finally 复位。
4. 成绩页加载中也能下拉刷新，公告详情页销毁 WebView 时不再做多余的清空操作。加载与空态分支补上 scrollModifier，onRelease 只保留 stopLoading 与 destroy。

## 架构改进
1. 导出文本到文件收成一个公共入口，仪表盘、详情页与几个记录页共用。新增 TextExportLauncher.kt 与 rememberTextExportLauncher，统一 CreateDocument、UTF-8 写入与提示。
2. 房间选择弹窗抽成公共组件，我的寝室与充值页共用。新增 RoomSwitchSheet.kt，把标题、提示、绑定解绑入口与房间列表收成一处。
3. 页面返回清理改由生命周期回调触发，手势返回也能清干净。详情页与充值记录页把清理从 onBack 移到 DisposableEffect。
4. 毛玻璃底色改由组件按风格自动取，页面不再逐个传参。BlurScaffold 默认值按 LocalUiStyle 在 miuix surface 与 M3 surfaceContainer 之间切换。
5. 重复 UI 抽成私有组件，并清掉多余的空回调与调试残留。仪表盘合并 UsageColumn、公告合并 MetaItem、首页合并 GridItemLabel 与 RemoveBadge，ReLoginContent 的 onReLogin 给默认实现后删掉 23 处空 lambda，同时移除 TabPerf 计时与重复 OptIn。
