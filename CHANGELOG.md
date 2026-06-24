# 更改日志

## 新增功能
- 支付确认页面引入三阶段状态机（PaymentPhase: SELECT_METHOD → WAITING_PAYMENT → PAYMENT_SUCCESS），用户选好支付方式后自动进入半屏弹窗，轮询检测到支付成功后自动切换到成功卡片，流程更清晰
- PaymentOverlay 全面重构为模块化架构，拆分为 BottomSheetDrawer / PaymentWebViewContent / OverlayBackdrop 三个子组件，WebView 使用 `key()` 管理生命周期，新增 LinearProgressIndicator 实时显示页面加载进度
- 支付覆盖层支持手势下滑关闭（swipe-to-dismiss），通过 detectVerticalDragGestures + lerp 动画实现平滑拖拽关闭效果
- 预设金额按钮新增 `contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)`，增大触控区域
- 充值错误提示全面国际化：RechargeViewModel / CardRechargeViewModel / PaymentFlowDelegate 中所有硬编码中文错误字符串替换为 Android string resources，支持中/英/日/法/阿拉伯/繁体中文 7 种语言
- 新增 18 条充值错误提示字符串（error_enter_student_id、error_no_room、error_invalid_amount、error_amount_exceeded_recharge、error_amount_exceeded_card 等），覆盖所有充值流程中的错误场景
- 校园卡充值模块新增 9 条 UI 字符串（card_recharge_student_id_label、card_recharge_custom_amount_label、card_recharge_next_step 等），补充 ar/fr/ja/zh-rTW 四语言翻译
- 设置页新增 20 条存储清理相关字符串（storage_clear_title、storage_clear_safe_group、storage_clear_caution_group 等），补充 ar/fr/ja/zh-rTW 四语言翻译
- 登录模块新增 3 条账号管理字符串（account_manager_title、account_manager_current、account_manager_add），补充 ar/fr/ja/zh-rTW 四语言翻译
- 桌面快捷方式新增 2 条提示字符串（shortcut_not_supported、shortcut_permission_hint），补充 ar/fr/ja/zh-rTW 四语言翻译
- 全局新增 common_select_date（选择日期）字符串，7 语言全覆盖
- 支付确认页新增 payment_continue（继续支付）按钮文本
- 新增 7 条充值记录导出相关字符串（record_export_title、record_export_empty、record_export_total 等）

## Bug 修复
- 修复 RechargeViewModel 和 CardRechargeViewModel 中错误提示为硬编码中文导致非中文用户无法理解错误信息的问题，改用 getString(R.string.xxx) 实现多语言错误提示
- 修复 PaymentFlowDelegate 中"提交支付失败""订单已关闭""查询超时"三条错误信息不支持国际化的问题，新增 getString 回调参数统一处理

## 架构改进
- RechargeViewModel 和 CardRechargeViewModel 从 ViewModel 改为 AndroidViewModel，注入 Application 上下文以支持 getString() 资源访问
- PaymentFlowDelegate 构造函数新增 `getString: (Int) -> String` 回调参数，委托方可传入 Application.getString 实现错误信息国际化
- PaymentOverlay 将全屏 WebView 包装改为 BottomSheetDrawer 底部抽屉模式，通过 ComposeView 内嵌 AndroidView 实现 WebView，支持 loading 状态、进度条和 WebViewClient/WebChromeClient 生命周期管理
- PaymentConfirmScreen 使用 PaymentPhase 枚举替代原有的 showPaymentOverlay 布尔值，将"选择支付方式→等待支付→支付成功"三阶段解耦，每阶段独立控制 UI 渲染
- 预设金额显示格式从 `%1$d元` / `%1$d CNY` 统一为 `￥%1$d`，所有语言共享相同货币符号格式
- .gitignore 清理冗余分隔线，将 /backup 规则替换为 fortest/

## 依赖变更
- VERSION_CODE 从 1346 升至 1354
