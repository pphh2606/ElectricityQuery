package edu.cqwu.electricity.common.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R

/**
 * 列表统计行：左「已加载 x 条」右「第 x/n 页」。
 *
 * 账单、人员搜索、缴费订单、登录日志、全校课表、通知公告共用。
 * 数据回来之前这一行并不存在，直接 `if` 会让它整行「啪」地插进来、把列表顶一下，
 * 所以出现/消失统一带 180ms/120ms 的淡入展开、淡出收起动画（与首页分类条等一致）。
 * 排版：fillMaxWidth + padding(8dp, 16dp) + SpaceBetween，bodySmall / onSurfaceVariant。
 * 左侧文案可能较长（如「共 8565 条通知」、搜索结果），故左侧可收缩、超长省略，
 * 保证右侧页码始终完整可见。
 *
 * @param loadedCount 已加载条数
 * @param currentPage 当前页码（显示用，从 1 开始，由调用方保证）
 * @param totalPages 总页数
 * @param loadedText 左侧文案，null 时用通用的「已加载 x 条」（人员搜索传「共搜索到 x 人」，
 *                   通知公告传「共 x 条通知」）
 * @param visible 是否显示；调用方把原来的显示条件传进来即可，本组件负责带动画地出现
 */
@Composable
fun ListStatsRow(
    loadedCount: Int,
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
    loadedText: String? = null,
    visible: Boolean = true,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + expandVertically(tween(180)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = loadedText ?: pluralStringResource(R.plurals.common_loaded_count, loadedCount, loadedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = stringResource(R.string.common_page_info, currentPage, totalPages),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}
