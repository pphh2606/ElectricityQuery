# 更改日志

## 新增功能
- 登录页找回密码改为 WebViewBottomSheet 半屏弹窗，支持文件上传和返回手势关闭，用户无需跳转到全屏 WebView 页面即可完成密码找回
- 校园卡充值页面进入时自动填充已登录用户的学号并查询卡信息，减少用户手动输入步骤
- 校园卡充值页面新增下拉刷新（PullToRefreshBox），支持手动刷新卡信息和余额
- 新增通用支付确认页面（PaymentConfirmScreen），电费充值和校园卡充值共用同一套支付方式选择 + 确认支付 UI
- 新增通用支付覆盖层（PaymentOverlay），统一处理支付宝 sbHtml 表单自动提交和微信 mwebUrl 跳转外部 App 的支付流程
- 新增支付流程委托（PaymentFlowDelegate），封装支付方式选择、提交支付、订单状态轮询（2~5 秒递增间隔，最长 5 分钟）等通用逻辑

## Bug 修复
- BottomSheetDialog 关闭动画使用 try/finally 确保 isHiding 状态重置，修复键盘弹出触发 expand() 取消 hide() 动画后 scrim 永久遮挡屏幕的问题
- BottomSheetDialog 键盘弹出自动展开时检查 !isHiding 条件，防止 expand() 取消正在进行的 hide() 动画导致状态异常
- 支付错误提示后自动调用 clearPaymentError / clearQueryError / clearCreateOrderError，避免 Snackbar 重复弹出

## 架构改进
- 移除 ElectricityRepository 和 CardRechargeRepository 仓库层，ViewModel 直接调用 API 层，减少一层无实质逻辑的转发（之前 Repository 仅透传 API 调用），简化调用链约 160 行代码
- HttpClientFactory 从 data/network/ 迁移至 data/network/pay/ 包下，CardRechargeApi 迁移至 pay/cardrecharge/，ElectricityApi 和 RSAEncrypt 迁移至 pay/electricityrecharge/，支付相关网络代码统一归口管理
- 新增 PayApiBase 抽象基类，提取支付 API 的公共逻辑：PAY_DOMAIN 常量、请求构建（buildBaseRequest）、Token 缺失自动重试（autoRetry）、泛型响应解析（parseApiResponse），消除 CardRechargeApi 和 ElectricityPayApi 之间的重复代码
- CardRechargeModels 中多个结构相同的 { messageCode, message, data } 包装类统一为泛型 ApiResponse<T>，定义在 PayApiBase.kt 中
- 预设金额网格（AmountGrid）从 RechargeScreen 内联代码提取为独立 Composable 组件，电费充值和校园卡充值共用，预设金额统一为 20/50/100/200/500/1000 元
- 支付页面（CardPaymentScreen、PaymentSelectionScreen）从 ~450 行和 ~700 行大幅精简，复用 PaymentConfirmScreen + PaymentOverlay 组件，核心 UI 逻辑集中在 paycommom 包
- 支付流程状态从各 ViewModel 的 UiState 散落字段（selectedPaymentMethod / isPaying / sbHtml / mwebUrl / orderStatus / isPolling）统一收拢为 PaymentState data class，通过 PaymentFlowDelegate 委托管理
- CardRechargeViewModel 支付相关方法（selectPaymentMethod、submitPayment、startPollingOrderStatus）委托给 PaymentFlowDelegate 实现，与 RechargeViewModel 共享同一套支付流程逻辑
- 删除 PaymentWebViewEngine.kt，其支付页面加载和 URL 拦截逻辑迁移至 PaymentOverlay 组件
- 多个 ViewModel（ElectricityViewModel、DetailViewModel、MyRoomViewModel）和 API 文件（CasAuthApi、QrLoginApi、CampusphereApi、FeeServiceHallApi 等）的 HttpClientFactory import 路径统一更新

## 新增的文件
- PayApiBase.kt — 支付 API 公共基类，包含 autoRetry / buildBaseRequest / ApiResponse<T> 泛型响应解析
- ElectricityPayApi.kt — 电费支付 API，从 ElectricityApi 中拆分出 createRechargeOrder / getOrderStatus 等支付相关方法
- PaymentFlowDelegate.kt — 支付流程委托，封装 submitPayment / startPollingOrderStatus / selectAmount / clearPaymentState 等通用方法
- PaymentState.kt — 支付流程共享状态（selectedMethod / isProcessing / sbHtml / mwebUrl / orderStatus / isPolling）
- AmountGrid.kt — 预设充值金额网格组件，电费和校园卡充值共用
- PaymentConfirmScreen.kt — 支付确认页面（金额 + 支付方式选择 + 确认按钮 + 订单状态展示）
- PaymentOverlay.kt — 支付 WebView 覆盖层（支付宝表单提交 + 微信跳转 + 自定义 scheme 拦截）
- WebViewBottomSheet.kt — 半屏 WebView 弹窗，支持文件上传（WebChromeClient）、返回手势关闭、标题栏

## 删除的文件
- CardRechargeModels.kt — 多个响应包装类合并为 PayApiBase 中的 ApiResponse<T> 泛型类
- CardRechargeRepository.kt — 仓库层移除，ViewModel 直接调用 CardRechargeApi
- ElectricityRepository.kt — 仓库层移除，ViewModel 直接调用 ElectricityApi
- PaymentWebViewEngine.kt — 支付 WebView 引擎逻辑迁移至 PaymentOverlay 组件

## 依赖变更
- VERSION_CODE 从 1319 升至 1346
