# 更改日志

## 新增功能
- 卡中心「校园卡充值」入口从 WebView 跳转改为原生页面，用户无需离开 App 即可完成充值全流程，体验更流畅
- 新增校园卡充值页面（CardRechargeScreen），支持输入学号查询卡信息、选择预设/自定义金额、一键创建订单
- 新增校园卡支付页面（CardPaymentScreen），支持微信和支付宝两种支付方式选择，支付宝通过 WebView 内嵌表单自动提交，微信通过 mwebUrl 跳转外部 App 完成支付
- 支付完成后自动轮询订单状态（2~5 秒递增间隔，最长 5 分钟），从外部 App 返回后通过 LifecycleEventObserver 自动触发轮询，页面实时展示"支付成功"状态
- 电费充值预设金额调整为 20/50/100/200/500/1000 元，适配大额充值场景

## Bug 修复
- 电费接口请求头移除 `sec-ch-ua`、`sec-ch-ua-mobile`、`sec-ch-ua-platform` 三个浏览器指纹头字段，避免因 WebView 版本差异导致服务器校验失败

## 架构改进
- 校园卡充值采用完整的三层架构：API 层（CardRechargeApi）→ 仓库层（CardRechargeRepository）→ ViewModel（CardRechargeViewModel），认证复用 FeeServiceHallApi 的 JWT Token 体系，Token 缺失时自动获取并重试（autoRetry 模式）
- CardRechargeViewModel 在 AppNavGraph 级别共享实例，充值选择页和支付页使用同一 ViewModel，订单创建后通过 StateFlow 传递数据，避免页面间序列化开销
- 支付页面使用 `hasNavigatedToPayment` 标记防止预测性返回手势取消时重复导航，参考 Compose Navigation 最佳实践
- WebView 覆盖层支持 `alipays://`、`weixin://`、`intent://` 等自定义 scheme 拦截，通过 `WebViewUrlUtil.openCustomSchemeUrl()` 统一处理外部 App 跳转

## 新增的文件
- CardRechargeApi.kt — 校园卡充值 API 封装（queryBasicInfo / queryTradeChannels / createOrder / toPayOrderTrade / queryOrderStatus）
- CardRechargeModels.kt — 校园卡充值数据模型（CardBasicInfo / CardRechargeOrderResult / CardPaymentChannel / CardPaymentResult / CardOrderStatus 及内部 API 响应包装）
- CardRechargeRepository.kt — 校园卡充值数据仓库层，封装 API 调用和默认参数
- CardRechargeScreen.kt — 校园卡充值 UI 页面（学号输入 + 校园卡信息展示 + 预设/自定义金额选择）
- CardRechargeViewModel.kt — 校园卡充值 ViewModel，管理充值全流程状态（查询 → 选金额 → 创建订单 → 支付 → 轮询）
- CardPaymentScreen.kt — 校园卡支付 UI 页面（支付方式选择 + WebView 支付执行 + 订单状态展示）

## 依赖变更
- VERSION_CODE 从 1300 升至 1319
