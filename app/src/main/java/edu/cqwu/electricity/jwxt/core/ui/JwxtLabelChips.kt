package edu.cqwu.electricity.jwxt.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.ui.SectionFilterChip

/**
 * chip 行的一项：名称 + 数量（文案形如「全部 11」）。
 *
 * 首页的「服务分类」与更多服务页的「分组」都只需要这两个值，所以 chip 组件按它取参，
 * 不绑死在某一页的 UI 模型上。
 */
internal data class JwxtChipLabel(val name: String, val count: Int)

/**
 * chip 行：**页面内部的分类/分组切换**，随内容一起滚动。
 *
 * 它只代表某一页里的分组维度（首页是服务分类、更多服务页是全部/查询/申请），不是整页导航，
 * 所以不像项目首页 `HomeSectionIndex` 那样固定在顶栏下方——那会让人误以为它是全站分类。
 *
 * 文案形如「全部 11」（名称 + 数量），样式复用 `common/ui/SectionFilterChip.kt`，
 * 保证与其它页面观感统一。首页与更多服务页共用，故放在共享层 `jwxt/core/ui/`。
 */
@Composable
internal fun JwxtLabelChips(
    labels: List<JwxtChipLabel>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    if (labels.isEmpty()) return

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(labels) { index, label ->
            SectionFilterChip(
                text = stringResource(R.string.jwxt_chip_label, label.name, label.count),
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}
