package edu.cqwu.electricity.accountmanagerv2

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.ui.AppOutlinedTextField
import edu.cqwu.electricity.common.ui.AppScaledAlertDialog
import edu.cqwu.electricity.common.ui.LoadingDialog
import edu.cqwu.electricity.common.ui.PasswordRecoverySheet
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import edu.cqwu.electricity.common.util.ToastUtils
import edu.cqwu.electricity.common.util.restartApp

/**
 * 修改密码页 — 布局对应 CAS 网页 mobilePasswordChange.do：
 * 顶部提示文字 → 旧密码 / 新密码 / 确认新密码 / 验证码（图片可点击换一张）→ 右下角保存按钮。
 *
 * 修改成功后服务端会注销当前账号登录信息，弹出「下线通知」原生弹窗（不可关闭），
 * 点「确定」重启应用；重启后启动 Cookie 验证检测到会话失效并自动跳转登录页。
 * 本地登录态不主动清理，仅引导用户重新登录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordChangeScreen(
    onBack: () -> Unit,
    onReLogin: () -> Unit,
    viewModel: PasswordChangeViewModel = viewModel(),
) {
    val snackbar = LocalSnackbarController.current
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showRecoverySheet by remember { mutableStateOf(false) }

    // 失败/提示消息（Snackbar 展示后消费）
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.show(it, if (state.messageIsError) ToastUtils.Type.ERROR else ToastUtils.Type.SUCCESS)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        // 键盘弹出时让出 IME 高度，避免遮挡右下角"保存"按钮
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.password_change_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = currentTopBarColors(),
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                state.requiresReLogin -> ReLoginContent(
                    requiresReLogin = true,
                    onReLogin = onReLogin,
                )
                state.loadError != null && state.salt.isEmpty() -> ReLoginContent(
                    errorMessage = state.loadError,
                    requiresReLogin = false,
                    onReLogin = {},
                    onRetry = viewModel::refresh,
                )
                else -> PasswordChangeContent(
                    state = state,
                    viewModel = viewModel,
                    onForgotPassword = { showRecoverySheet = true },
                )
            }
        }
    }

    // 修改成功：服务端已注销当前账号登录态 → 弹「下线通知」原生弹窗。
    // 使用原生 AlertDialog 而非底部弹窗：MD3 ModalBottomSheet 会被系统预测式返回动画
    // 直接驱动关闭（绕过 onDismissRequest），无法实现"不可关闭"的强制确认语义。
    // 弹窗不可通过点击空白 / 返回键（含预测式返回）关闭，仅「确定」→ 重启应用
    // （重启后启动验证会检测到 cookie 已失效并自动跳转登录页）。
    if (state.changeSucceeded) {
        AppScaledAlertDialog(
            onDismissRequest = { /* 强制确认：点击空白不关闭 */ },
            properties = DialogProperties(dismissOnBackPress = false),
            title = { Text(text = stringResource(R.string.password_change_relogin_title)) },
            text = { Text(text = stringResource(R.string.password_change_relogin_message)) },
            confirmButton = {
                TextButton(onClick = { restartApp(context) }) {
                    Text(text = stringResource(R.string.common_confirm))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }

    if (state.isSaving) {
        LoadingDialog(message = stringResource(R.string.password_change_saving))
    }

    // 找回密码弹窗（与登录页共用，含半屏 WebView）
    PasswordRecoverySheet(
        visible = showRecoverySheet,
        onDismissRequest = { showRecoverySheet = false },
    )
}

@Composable
private fun PasswordChangeContent(
    state: PasswordChangeUiState,
    viewModel: PasswordChangeViewModel,
    onForgotPassword: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 对应网页 form-tip + 密码强度规则（pwdStrengthPromot）
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.password_change_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.password_change_strength),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 对应网页 form-group：旧密码
            PasswordField(
                value = state.oldPassword,
                onValueChange = viewModel::onOldPasswordChange,
                label = stringResource(R.string.password_change_old_label),
                icon = Icons.Outlined.Lock,
                enabled = !state.isSaving,
            )

            // 对应网页 form-group：新密码
            PasswordField(
                value = state.newPassword,
                onValueChange = viewModel::onNewPasswordChange,
                label = stringResource(R.string.password_change_new_label),
                icon = Icons.Outlined.VerifiedUser,
                enabled = !state.isSaving,
                isError = state.passwordError != null,
            )

            // 对应网页 form-group：确认新密码
            PasswordField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = stringResource(R.string.password_change_confirm_label),
                icon = Icons.Outlined.VerifiedUser,
                enabled = !state.isSaving,
                isError = state.confirmError != null,
            )

            // 对应网页 form-group：验证码（输入框 + 图片，点击图片换一张）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppOutlinedTextField(
                    value = state.captcha,
                    onValueChange = viewModel::onCaptchaChange,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSaving,
                    singleLine = true,
                    label = { Text(stringResource(R.string.password_change_captcha_label)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    isError = state.captchaError != null,
                )
                // 对应网页 captchaImg，点击换验证码
                // 尺寸与左侧输入框等高（M3 outlined 为 56dp），使上下边缘平齐；等比放大的宽度按 95:38 推算为 140dp
                Box(
                    modifier = Modifier
                        .size(width = 140.dp, height = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = viewModel::refreshCaptcha),
                    contentAlignment = Alignment.Center,
                ) {
                    state.captchaImage?.let { bytes ->
                        val bitmap = remember(bytes) {
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = stringResource(R.string.password_change_captcha_label),
                                modifier = Modifier.fillMaxSize(),
                                // 硬拉伸铺满 140×56：上下左右全贴边、不裁内容，代价是不再保持原图比例
                                contentScale = ContentScale.FillBounds,
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.password_change_captcha_click_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 对应网页 form-footer：忘记密码 + 保存（右下角固定）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onForgotPassword,
                enabled = !state.isSaving,
            ) {
                Text(
                    text = stringResource(R.string.password_change_forgot),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
            ) {
                Text(
                    text = stringResource(R.string.password_change_save),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    isError: Boolean = false,
) {
    AppOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        isError = isError,
    )
}
