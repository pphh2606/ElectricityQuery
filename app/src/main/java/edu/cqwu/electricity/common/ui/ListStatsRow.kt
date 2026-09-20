package edu.cqwu.electricity.common.ui

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
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R

/**
 * 列表统计行：左「已加载 x 条」右「第 x/n 页」。
 *
 * 账单、人员搜索、缴费订单、登录日志、全校课表、通知公告共用。
 * 是否显示、显示在哪个位置由调用方决定（各页条件不同），本组件只负责这一行的排版：
 * fillMaxWidth + padding(8dp, 16dp) + SpaceBetween，bodySmall / onSurfaceVariant。
 *
 * @param loadedCount 已加载条数
 * @param currentPage 当前页码（显示用，从 1 开始，由调用方保证）
 * @param totalPages 总页数
 * @param loadedText 左侧文案，null 时用通用的「已加载 x 条」（人员搜索传「共搜索到 x 人」）
 */
@Composable
fun ListStatsRow(
    loadedCount: Int,
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
    loadedText: String? = null,
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
        )
        Text(
            text = stringResource(R.string.common_page_info, currentPage, totalPages),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
