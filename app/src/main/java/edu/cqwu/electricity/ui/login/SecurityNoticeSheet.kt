package edu.cqwu.electricity.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.ui.components.BottomSheetDialog

/**
 * 安全说明底部弹窗。
 *
 * 展示登录流程的 5 条安全声明，纯展示组件，无内部状态。
 */
@Composable
fun SecurityNoticeSheet(
    visible: Boolean = true,
    onDismiss: () -> Unit,
) {
    BottomSheetDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.login_security_notice_title),
    ) {
        val noticeItems = listOf(
            R.string.login_security_notice_1,
            R.string.login_security_notice_2,
            R.string.login_security_notice_3,
            R.string.login_security_notice_4,
            R.string.login_security_notice_5,
            R.string.login_security_notice_6,
            R.string.login_security_notice_7,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            noticeItems.forEach { resId ->
                Text(
                    text = stringResource(resId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
