package edu.cqwu.electricity.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.compositionLocalOf
import edu.cqwu.electricity.util.ToastUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 自定义 SnackbarVisuals，将类型信息嵌入每个 Snackbar 实例
 *
 * 避免 [SnackbarController] 使用全局状态记录类型导致多 Snackbar 排队时颜色错乱。
 * 颜色信息直接从 [data.visuals] 提取，与 Snackbar 实例一一绑定。
 */
class CustomSnackbarVisuals(
    override val message: String,
    val type: ToastUtils.Type,
    override val actionLabel: String? = null,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
    override val withDismissAction: Boolean = false,
) : SnackbarVisuals

/**
 * 全局 Snackbar 控制器
 *
 * 通过 [LocalSnackbarController] CompositionLocal 提供给所有子 Composable 使用。
 * 生命周期跟随 [edu.cqwu.electricity.ui.navigation.AppShell]，位于 NavHost 之上，
 * 不受页面导航影响，切换页面时 Snackbar 动画持续播放。
 *
 * 使用方式：
 * ```kotlin
 * val snackbar = LocalSnackbarController.current
 * snackbar.show("操作成功", ToastUtils.Type.SUCCESS)
 * ```
 *
 * 内部持有独立 [CoroutineScope]，不依赖任何 Composable 的 rememberCoroutineScope()，
 * 避免页面导航时 scope 被 cancel 导致 Snackbar 提前消失。
 */
class SnackbarController {
    val hostState = SnackbarHostState()

    /** 内部协程作用域，生命周期与 [SnackbarController] 实例相同 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * 显示一条 Snackbar 消息
     *
     * @param message 显示文本
     * @param type 类型（SUCCESS / ERROR），影响背景颜色
     */
    fun show(message: String, type: ToastUtils.Type = ToastUtils.Type.ERROR) {
        show(message = message, type = type, actionLabel = null, onAction = null)
    }

    /**
     * 显示一条带 Action 按钮的 Snackbar 消息
     *
     * @param message 显示文本
     * @param type 类型（SUCCESS / ERROR），影响背景颜色
     * @param actionLabel 操作按钮文字（如"打开"），null 时不显示按钮
     * @param onAction 点击操作按钮时的回调
     */
    fun show(
        message: String,
        type: ToastUtils.Type = ToastUtils.Type.ERROR,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(
                CustomSnackbarVisuals(
                    message = message,
                    type = type,
                    actionLabel = actionLabel,
                    duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
                )
            )
            if (result == SnackbarResult.ActionPerformed) {
                onAction?.invoke()
            }
        }
    }

}

/**
 * CompositionLocal 提供者
 *
 * 在 [edu.cqwu.electricity.ui.navigation.AppShell] 中通过 CompositionLocalProvider 注入。
 * 各 Screen 中通过 `LocalSnackbarController.current` 获取实例。
 */
val LocalSnackbarController = compositionLocalOf { SnackbarController() }
