package edu.cqwu.electricity.common.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.common.settings.LocalAppSettingsState
import edu.cqwu.electricity.common.settings.ReduceMotion

/**
 * 未选中项图标的下移补偿量。
 *
 * M3 在"带文字"的条目里会把图标上移一点给文字让位；要让未选中项的图标看起来回到格子中间，
 * 就得往下推回同样的距离。这个值 M3 没有对外暴露，真机上若觉得未选中项的图标偏高/偏低，改这里即可。
 */
private val UNSELECTED_ICON_OFFSET = 8.dp

/**
 * 底栏条目（三处底部 tab 共用）。
 *
 * 是 [RowScope] 扩展：M3 的 `NavigationBarItem` 本身就是 RowScope 扩展（底栏内部是 Row），
 * 所以这里跟着声明，调用点写在 `NavigationBar { … }` 里即可。
 *
 * 默认行为与 M3 原生一致：图标 + 文字常显（此时偏移恒为 0，与改造前逐像素一致）。
 * 打开「底栏只显示选中项的文字」后：文字只留给选中项，图标在「居中 ↔ 上移」之间平滑移动。
 *
 * 为什么文字要一直占位、而不是未选中时把 label 传 null：M3 里 label 为空会切到另一条布局分支
 * （`placeIcon`），图标位置随之瞬移、没法做动画。这里让文字常驻（未选中时透明），
 * 布局分支保持恒定，位移才可控。副作用是读屏仍能念出每个 tab 的名字，对无障碍是加分项。
 */
@Composable
fun RowScope.AppNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    val appSettings = LocalAppSettingsState.current
    val selectedOnly = appSettings.tabLabelSelectedOnly
    val animate = appSettings.reduceMotion != ReduceMotion.ON

    // 未选中且开启「仅选中项显示文字」时，把图标往下推回格子中间
    val iconOffset by animateDpAsState(
        targetValue = if (!selectedOnly || selected) 0.dp else UNSELECTED_ICON_OFFSET,
        animationSpec = if (animate) spring() else snap(),
    )
    // 文字瞬时显隐（本次只要求图标做移动动画），关闭开关时恒为可见
    val labelAlpha = if (!selectedOnly || selected) 1f else 0f

    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.offset(y = iconOffset),
            )
        },
        label = {
            Text(
                text = label,
                modifier = Modifier.alpha(labelAlpha),
            )
        },
    )
}
