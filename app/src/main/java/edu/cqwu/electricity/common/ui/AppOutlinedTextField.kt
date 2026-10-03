package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.TextFieldLabelScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect

/**
 * 项目统一的轮廓输入框：12dp 圆角，且 label 浮起后停在 leadingIcon 右侧（与输入文字左对齐）。
 *
 * 背景：项目使用的 material3 1.4.0 中，`OutlinedTextField(value: String, ...)` 这个重载没有
 * `labelPosition` 参数，浮起后的 label 水平位置被固定在 contentPadding.start（16dp），会滑到
 * leadingIcon 正上方压住图标；只有 `OutlinedTextField(state: TextFieldState, ...)` 重载开放了
 * `labelPosition`。因此这里在组件内部桥接一层 [androidx.compose.foundation.text.input.TextFieldState]：
 * 输入过程完全由内部状态承载（光标与输入法组词不受外部回写干扰），外部改写（刷新清空、加载回填）
 * 经 [LaunchedEffect] 单向灌入，用户输入再经 snapshotFlow 回传给调用方，对调用方仍是
 * value / onValueChange 形态。
 *
 * @param value 外部持有的文本（ViewModel 状态）
 * @param onValueChange 文本变化回调（仅在用户输入导致变化时触发）
 * @param modifier 外部修饰符
 * @param enabled 是否可编辑
 * @param singleLine 单行输入
 * @param isError 错误态，错误色由 Material 主题给出
 * @param label 输入框标签（浮起后停在 leadingIcon 右侧）
 * @param leadingIcon 前置图标；为空时保持 Material 默认的 label 对齐方式
 * @param supportingText 辅助或错误提示文本
 */
@Composable
fun AppOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    isError: Boolean = false,
    label: @Composable (TextFieldLabelScope.() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
) {
    val fieldState = rememberTextFieldState(value)
    val latestValue by rememberUpdatedState(value)

    // 外部 → 内部：仅在确有差异时写入，避免打断正在进行的输入
    LaunchedEffect(value) {
        if (fieldState.text.toString() != value) {
            fieldState.setTextAndPlaceCursorAtEnd(value)
        }
    }

    // 内部 → 外部：与最新外部值比较，外部回填自身引起的发射不会再回调一次
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }
            .collect { if (it != latestValue) onValueChange(it) }
    }

    OutlinedTextField(
        state = fieldState,
        modifier = modifier,
        enabled = enabled,
        labelPosition = rememberIconAlignedLabelPosition(hasLeadingIcon = leadingIcon != null),
        label = label,
        leadingIcon = leadingIcon,
        supportingText = supportingText,
        isError = isError,
        lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.Default,
        shape = RoundedCornerShape(12.dp),
    )
}

/**
 * 让浮起后的 label 与输入文字左对齐（即停在 leadingIcon 右侧）的对齐策略。
 *
 * 数值依据（material3 1.4.0 源码 OutlinedTextField.kt 的 OutlinedTextFieldMeasurePolicy）：
 * - 展开态 label 的水平位置 = leadingPlaceable.width + max(0, startPadding - iconPadding)
 *   = 48dp + max(0, 16dp - 12dp) = 52dp，正好是输入文字的起点；
 * - 浮起态 label 的水平位置 = minimizedAlignment 的结果 + contentPadding.start = 16dp，
 *   与 leadingIcon 无关，因此默认会滑到图标正上方；
 * - 常量来源：contentPadding 四边为 TextFieldPadding = 16dp；leadingIcon 占位取自
 *   `Box(Modifier.layoutId(LeadingId).minimumInteractiveComponentSize())` = 48dp；
 *   iconPadding = (48dp - 24dp) / 2 = 12dp。
 * 两端相差 36dp，只能在这里补回来。若项目将来覆盖 LocalMinimumInteractiveComponentSize，
 * 48dp 这一项会随之变化，36dp 需同步调整。
 *
 * 必须 remember：每次重组新建 Alignment 实例会让内部 measurePolicy 的 remember 失效。
 */
@Composable
private fun rememberIconAlignedLabelPosition(hasLeadingIcon: Boolean): TextFieldLabelPosition {
    val density = LocalDensity.current
    return remember(density, hasLeadingIcon) {
        if (!hasLeadingIcon) {
            TextFieldLabelPosition.Attached()
        } else {
            val leadingIconAlignedOffset = with(density) { 36.dp.roundToPx() }
            TextFieldLabelPosition.Attached(
                minimizedAlignment = object : Alignment.Horizontal {
                    override fun align(size: Int, space: Int, layoutDirection: LayoutDirection): Int {
                        val maxOffset = (space - size).coerceAtLeast(0)
                        return if (layoutDirection == LayoutDirection.Ltr) {
                            leadingIconAlignedOffset.coerceAtMost(maxOffset)
                        } else {
                            (space - size - leadingIconAlignedOffset).coerceIn(0, maxOffset)
                        }
                    }
                },
            )
        }
    }
}
