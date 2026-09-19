package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R

/**
 * 列表底部统一的分页加载提示（认证日志 / 缴费订单 / 公告 / 课表查询 / 留言 / 查找人员等共用）。
 *
 * 形态：
 * - [isLoadingMore]：小型转圈 + 「加载中...」；
 * - [hasMore] 且传了 [onLoadMore]：可点的「上滑加载更多」（自动加载的页面可不传回调）；
 * - [hasMore] 但没传回调：什么都不显示；
 * - 其余：一句「╮(╯3╰)╭再怎么找也没有啦」。
 *
 * 「列表为空时不显示没有更多」由调用方自己包 `if` —— 各页面对"空"的判定不同（有的看条数、
 * 有的看是否加载过），组件不替它们猜。
 */
@Composable
fun PagingFooter(
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoadingMore -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.common_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            hasMore && onLoadMore != null -> Text(
                text = stringResource(R.string.common_swipe_load_more),
                modifier = Modifier.clickable(onClick = onLoadMore),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )

            hasMore -> Unit

            else -> Text(
                text = stringResource(R.string.common_no_more),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
