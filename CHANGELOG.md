# 更改日志

## 新增功能
- 订单详情新增「继续支付」和「关闭订单」按钮，待支付订单可直接在应用内继续支付或一键关闭
- 校园卡充值页面新增「其他充值方式」底部弹窗，支持今日校园 App 充值、应用内 H5 充值、浏览器 H5 充值三种渠道
- 校园卡充值页面底部新增「充值记录」和「消费记录」快捷入口，方便用户快速查看历史
- 缴费服务大厅支持通过 `initialTab` 参数直接跳转到订单 Tab，导航更精准
- 新增关闭订单 API（`FeeServiceHallApi.closeOrder`）和继续支付链接构建方法（`buildContinuePaymentUrl`）
- 订单模型 `OrderRecord` 新增 `projectId` 字段和 `isPendingPayment` 属性，用于识别待支付订单并构建继续支付链接

## Bug 修复
- 后端返回的 `PENDING_PAYMENT` 状态码之前无法正确显示为「待支付」，现已兼容该状态码并正确展示橙色标签
- 校园卡充值缺少金额上限校验，用户输入金额超出校园卡最大余额时无法提前拦截，现已在按钮层增加 `maxBalance` 校验
- 电费充值缺少单次充值 1000 元上限校验，自定义金额超限时无法及时提示，现已在 UI 层限制最大金额
- 校园卡充值页面订单创建错误弹两次 snackbar，移除了充值页面重复的错误监听 `LaunchedEffect`，统一由支付页面处理

## 架构改进
- 校园卡充值页面将订单创建逻辑后移到支付确认页面（`CardPaymentScreen`），充值页面只负责金额选择，职责更清晰
- 支付确认页面的内联加载指示器（`CircularProgressIndicator`）统一替换为 `LoadingDialog` 阻断式弹窗，交互体验与登录页一致
- `CardRechargeViewModel` 移除了 `hasNavigatedToPayment` 状态和 `markNavigatedToPayment()` 方法，消除了预测性返回手势导致的重复导航状态管理
- `RechargeViewModel` 和 `CardRechargeViewModel` 的 `createOrder()` 将金额校验逻辑前移至 UI 层（按钮禁用），ViewModel 不再做输入校验
- 订单详情组件 `OrderDetailContent` 新增 `onContinuePayment` / `onCloseOrder` 回调参数，支持条件渲染操作按钮

## 国际化
- 全部 6 种语言新增「充值记录」「消费记录」字符串（`strings_cardcenter.xml`）
- 全部 6 种语言新增「继续支付」「关闭订单」「正在关闭订单…」「订单已关闭」「关闭失败」共 5 个订单操作字符串（`strings_feehall.xml`）
- 全部 6 种语言将二维码页面「订单记录」更新为「消费记录」（`strings_qrcode.xml`）

## 依赖变更
- VERSION_CODE 从 1398 升至 1410
