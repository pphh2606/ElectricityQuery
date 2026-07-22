# 更改日志

## 新增功能
- 账单页面顶栏新增帮助图标（`HelpOutline`），点击弹出 `BottomSheetDialog` 说明账单加载耗时原因、数据来源及网页版入口，降低用户等待焦虑
- 电费充值提示弹窗新增「学号/编号」详细说明，帮助用户理解两种查询方式的区别及适用场景
- 充值提示弹窗第3条新增可点击的「其他充值方式」内部链接（`LinkAnnotation.Clickable`），点击后自动关闭提示并联动打开其他充值方式弹窗，无需手动操作
- 全部 6 种语言新增账单提示字符串 `bill_hint_title`、`bill_hint_item1`、`bill_hint_item2`、`bill_hint_item3`

## Bug 修复
- 首页服务图标（`CustomServiceIconItem`）远程图片加载失败时 `AsyncImage.onError` 回调为空，导致不显示任何内容，现已设置 `useFallbackIcon = true` 自动降级显示本地默认图标

## 架构改进
- 充值提示弹窗从 `RechargeScreen` 提升到 `ElectricityMainScreen` 统一管理，删除 `RechargeScreen` 中约 87 行重复的 `showInfoDialog` 弹窗代码，消除双源维护；通过新增 `triggerOtherRecharge` / `onOtherRechargeTriggered` 参数实现父组件向子组件的跨组件弹窗触发
- `NavGraph` 移除独立的 `Routes.RECHARGE` 路由及其 `animatedComposable` 注册，`RechargeScreen` 已作为 `ElectricityMainScreen` 的 Tab 子组件内嵌渲染，不再需要独立路由
- `ElectricityUiState` 移除冗余的 `selectedArea` 字段及 `selectBuilding()` 中的赋值逻辑，该状态从未被 UI 消费，属于死代码
- Models.kt 移除未使用的 `OrderStatusResponse` 包装类，直接使用 `OrderStatusData`；`RechargeTimeRange` 枚举注释从引用魔法数字改为描述用途
- `CardRechargeScreen` 调整 `PullToRefreshBox` 与 `Column` 的嵌套顺序，将「充值记录 | 消费记录」入口从可滚动区域移出并固定在页面底部，确保键盘弹出时始终可见
- `ElectricityMainScreen` 充值提示弹窗缩进格式修正，`BottomSheetDialog` 内容区域添加 `padding(horizontal = 16.dp)` 统一间距

## 国际化
- 全部 6 种语言重写充值提示文案：`recharge_hint_item1` 改为学号/编号说明，`recharge_hint_item2`~`item3` 重新编号，新增 `recharge_hint_item2_link` / `recharge_hint_item2_suffix` 替代原 `item1_link` / `item1_suffix`
- 全部 6 种语言优化充值输入框标签 `recharge_student_id_label`，从「请输入学号或 userId」改为「充值人学号或编号」，表述更直观
- 全部 6 种语言删除冗余的 `electricity_recharge_hint_item2` 和 `electricity_recharge_hint_item3`，内容已合并至 `strings_recharge.xml`

## 删除的文件
- `DeferredContent.kt` — 延迟内容加载组件，已无引用
- `TabScaffold.kt` — Tab 脚手架组件，已无引用

## 依赖变更
- VERSION_CODE 从 1410 升至 1417
