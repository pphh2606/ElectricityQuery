package edu.cqwu.electricity.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.ui.BottomSheetDialogV2

/**
 * 备份与恢复选择弹窗（带拖拽手柄）。
 *
 * 提供"导出备份 / 导入备份"两个入口，布局与"我有话说"的操作选择弹窗一致；
 * 该弹窗与 [BackupTransferScreen] 作为未来账号密码 / Cookie 迁移的同款载体。
 *
 * @param visible 是否显示
 * @param onDismiss 关闭回调
 * @param onExport 点击"导出备份"
 * @param onImport 点击"导入备份"
 */
@Composable
fun BackupRestoreSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    @androidx.annotation.StringRes exportLabelRes: Int = R.string.settings_backup_sheet_export,
    @androidx.annotation.StringRes importLabelRes: Int = R.string.settings_backup_sheet_import,
) {
    BottomSheetDialogV2(
        visible = visible,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BackupActionButton(
                icon = Icons.Outlined.FileDownload,
                text = stringResource(exportLabelRes),
                onClick = onExport,
                modifier = Modifier.weight(1f),
            )
            BackupActionButton(
                icon = Icons.Outlined.FileUpload,
                text = stringResource(importLabelRes),
                onClick = onImport,
                modifier = Modifier.weight(1f),
            )
        }

        // 取消按钮（对标"我有话说"操作弹窗）
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.textButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text(
                text = stringResource(R.string.common_cancel),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

/** 弹窗内的方形操作按钮（视觉对齐"我有话说"的操作按钮） */
@Composable
private fun BackupActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
