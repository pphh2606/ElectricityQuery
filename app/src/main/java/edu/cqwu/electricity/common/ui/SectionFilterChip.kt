package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * 通用筛选按钮：圆形 + surfaceContainerHigh 底色 + 无边框（MD3 FilterChip）。
 *
 * 供筛选/单选场景复用（认证日志筛选项、充值记录时间范围等），
 * 收敛各处重复的圆形筛选按钮实现。
 *
 * @param text 按钮文案
 * @param selected 是否选中（选中时 FilterChip 默认 primary 高亮）
 * @param onClick 点击回调
 * @param enabled 是否可点击（查询中禁用防重复）
 */
@Composable
fun SectionFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = null,
        label = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
