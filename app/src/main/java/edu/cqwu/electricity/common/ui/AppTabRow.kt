package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import edu.cqwu.electricity.common.settings.LocalUiStyle
import edu.cqwu.electricity.common.settings.UiStyle
import top.yukonga.miuix.kmp.basic.TabRow as MiuixTabRow

/**
 * 标题栏下方的二级切换栏，两套主题各自的实现：
 * - Material 3：`PrimaryTabRow`（透明底，与浮层融为一体）；
 * - MIUI：miuix 的 `TabRow`（胶囊滑动指示）。
 *
 * 字重交给两套主题自己的样式，调用方不要再手写 Bold。
 *
 * @param tabs 各页签文案
 * @param selectedIndex 当前选中页
 * @param onTabSelected 点击页签的回调（带页码，翻页动画由调用方负责）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalUiStyle.current == UiStyle.MIUI) {
        MiuixTabRow(
            tabs = tabs,
            selectedTabIndex = selectedIndex,
            onTabSelected = onTabSelected,
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        PrimaryTabRow(
            selectedTabIndex = selectedIndex,
            modifier = modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            divider = {},
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { onTabSelected(index) },
                    text = { Text(text = title) },
                )
            }
        }
    }
}
