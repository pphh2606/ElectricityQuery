package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight

/**
 * 各编辑页右下角的保存/提交按钮：全圆胶囊 + primary 填充。
 *
 * FloatingActionButtonDefaults.smallShape 是 16dp 圆角矩形，这里用 CircleShape 得到
 * 全圆胶囊（半径 = 40dp 高度的一半）；配色与「修改密码」页 split button 主按钮一致。
 */
@Composable
fun AppSaveFab(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    SmallExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(text, fontWeight = FontWeight.Bold) },
    )
}
