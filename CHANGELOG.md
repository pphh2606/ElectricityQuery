# 更改日志

## 新增功能
- 账单详情改为 `WebViewBottomSheet` 半屏弹窗，用户无需跳转全屏页面即可查看
- `WebViewBottomSheet` 新增拖拽手势：下滑低于 40% 屏高关闭，低于 50% 自动回弹半屏
- 登录页添加 `verticalScroll`，横屏/小屏下内容可滚动

## Bug 修复
- `WebViewBottomSheet` 的 `BackHandler` 条件从 `visible || isHiding` 修正为 `visible && !isHiding`，关闭动画中不再误拦截返回键

## 架构改进
- 充值页和主页从 `Column + verticalScroll` 迁移为 `LazyColumn + item{}`，减少初始组合开销
- `BillViewModel` 重构并发加载：H5 和 HTML 各服务不重叠的 Tab，互不覆盖缓存
- `ApiBusinessException` 移除未使用的 `code` 参数，调用方统一简化
- `WebViewUrlUtil` 移除 `isWechatPayUrl`、`isAlipayUrl`、`isShowselectUrl` 三个未使用方法

## 依赖变更
- VERSION_CODE 从 1354 升至 1369
