package edu.cqwu.electricity.common.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * 长按可选中文字、且**复制后自动取消选中**的选择容器。
 *
 * 为什么不直接用 [SelectionContainer]：Compose 的复制路径（`SelectionManager.copy()` →
 * `onCopyHandler`）只把选中文字写进剪贴板，并不重置自己的 `selection` 状态；`onRelease()`
 * 也只清 subselections、隐藏工具栏、回调 `onSelectionChange`。结果是点「复制」后菜单收起、
 * 但高亮与选择手柄仍残留，必须再点一下别处才消失——与原生 TextView（ActionMode 结束即清除）
 * 的表现不同。
 *
 * 为什么不用受控重载：foundation 1.9.5 里 `SelectionContainer(selection, onSelectionChange)`
 * 与 `Selection` 类型都是 `internal`，应用层拿不到。因此这里改为：监听系统剪贴板被写入
 * （即发生了一次复制，无论来自系统选择菜单还是
 * [edu.cqwu.electricity.common.util.copyToClipboard]），用 [key] 重建 [SelectionContainer]，
 * 让它内部的 `SelectionManager` 连同选区状态一起被丢弃，效果等同于清除选中。
 *
 * 注意：本组件**不要嵌套使用**，外层也不要在同一个选择区域内再包选择容器，嵌套会导致选择异常。
 *
 * @param modifier 作用于选择容器的修饰符
 * @param content 可选择的内容
 */
@Composable
fun SelectableContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 每次复制后自增，用于重建 SelectionContainer，清掉残留的高亮与手柄
    var resetToken by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    // 复制动作本身没有回调，只能通过剪贴板被写入来感知
    DisposableEffect(clipboard) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener { resetToken++ }
        clipboard?.addPrimaryClipChangedListener(listener)
        onDispose { clipboard?.removePrimaryClipChangedListener(listener) }
    }

    key(resetToken) {
        SelectionContainer(modifier = modifier) {
            content()
        }
    }
}
