package edu.cqwu.electricity.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 带 Switch 的设置条目，与 [SettingsEntry] 保持统一布局。
 *
 * [enabled] 为 false 时整行置灰、不可点——用于「系统不支持这项能力」的情况：
 * 只置灰不隐藏，用户才知道有这项设置。
 *
 * [onRowClick] 非空表示「这一行还能进入更细的设置」：此时**整行**都可点（水波纹铺满整行，
 * 和系统设置页的手感一致），右侧开关自己消费点击、只负责开与关，
 * 两者之间用一条竖线分隔，让用户一眼看出这是两个操作区。
 * 为空时保持原来的「点整行 = 切换开关」。
 *
 * [rowClickEnabled] 单独控制「能不能进下级」：模糊类设置里的约定是——
 * 系统不支持、或功能本身关着时，二级入口一并禁掉（此时点整行没有任何反应）。
 * 它和 [enabled] 分开，是因为开关的可用性（系统是否支持）与二级入口的可用性
 * （还要求功能已开启）并不总是同一条条件。
 */
@Composable
internal fun SettingsSwitchEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    onRowClick: (() -> Unit)? = null,
    rowClickEnabled: Boolean = true,
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = if (onRowClick != null) rowClickEnabled else enabled,
            ) {
                if (onRowClick != null) onRowClick() else onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 双操作区的分隔线：左侧「进入细项」、右侧「开/关」，避免用户以为点哪都只是切换开关
        if (onRowClick != null) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .width(1.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = contentAlpha)),
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
