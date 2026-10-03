package edu.cqwu.electricity.common.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R
import edu.cqwu.electricity.webview.ui.WebViewBottomSheet

/** 手机号找回密码页（CAS mobileGetPasswordController.do） */
private const val PHONE_RECOVERY_URL =
    "https://authserver.cqwu.edu.cn/authserver/mobileGetPasswordController.do"

/** 邮箱找回密码页（CAS moblieFindPwdByMailPage.do） */
private const val EMAIL_RECOVERY_URL =
    "https://authserver.cqwu.edu.cn/authserver/moblieFindPwdByMailPage.do"

/**
 * 找回密码弹窗：手机号找回 / 邮箱找回 → 半屏 WebView 打开学校官方页面。
 *
 * 登录页与「账号管理 → 修改密码」页共用；弹窗与 WebView 的显隐状态都在组件内部，
 * 调用方只需提供 [visible] 与关闭回调。
 */
@Composable
fun PasswordRecoverySheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    var webViewUrl by remember { mutableStateOf<String?>(null) }
    var webViewTitle by remember { mutableStateOf("") }

    val phoneRecoveryTitle = stringResource(R.string.login_method_phone_recovery)
    val emailRecoveryTitle = stringResource(R.string.login_method_email_recovery)

    BottomSheetDialogV2(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.login_password_recovery),
    ) {
        BottomSheetItem(
            icon = Icons.Outlined.Phone,
            title = phoneRecoveryTitle,
            onClick = {
                onDismissRequest()
                webViewTitle = phoneRecoveryTitle
                webViewUrl = PHONE_RECOVERY_URL
            }
        )
        BottomSheetItem(
            icon = Icons.Outlined.Email,
            title = emailRecoveryTitle,
            onClick = {
                onDismissRequest()
                webViewTitle = emailRecoveryTitle
                webViewUrl = EMAIL_RECOVERY_URL
            }
        )
    }

    WebViewBottomSheet(
        visible = webViewUrl != null,
        onDismissRequest = { webViewUrl = null },
        url = webViewUrl.orEmpty(),
        title = webViewTitle
    )
}
