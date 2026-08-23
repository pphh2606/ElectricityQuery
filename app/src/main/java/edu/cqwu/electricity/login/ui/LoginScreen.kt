package edu.cqwu.electricity.login.ui

import edu.cqwu.electricity.theme.ui.currentTopBarColors

import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.semantics.Role
import edu.cqwu.electricity.theme.ui.AppScaledDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.theme.ui.BottomSheetDialog
import edu.cqwu.electricity.theme.ui.BottomSheetItem
import edu.cqwu.electricity.theme.ui.LanguageSwitchButton
import edu.cqwu.electricity.theme.ui.LoadingDialog
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.theme.ui.LocalNavController
import edu.cqwu.electricity.webview.ui.WebViewBottomSheet
import edu.cqwu.electricity.theme.util.ToastUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch

/**
 * 登录界面
 *
 * 提供 CAS 统一认证登录功能，包含：
 * - 学号输入框
 * - 密码输入框（密码模式）
 * - 记住密码复选框
 * - 登录按钮
 * - 登录结果 Toast 提示
 * - 标题栏三点菜单：导入Cookie / 导出Cookie
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit = onBack,
    clearForm: Boolean = false,
    initialUsername: String? = null,
    loginViewModel: LoginViewModel = viewModel()
) {
    val uiState by loginViewModel.uiState.collectAsState()

    // 每次进入登录页时重置状态，防止 ViewModel 跨导航残留旧数据
    LaunchedEffect(Unit) {
        loginViewModel.resetState(clearForm = clearForm, initialUsername = initialUsername)
    }
    val context = LocalContext.current
    val resources = LocalResources.current
    val focusManager = LocalFocusManager.current
    val nav = LocalNavController.current

    val topBarColors = currentTopBarColors()

    // 三点菜单与凭据导入导出
    var showMenu by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    // 安全说明弹窗
    var showSecurityNotice by remember { mutableStateOf(false) }
    // 其他登录方式弹窗
    var showOtherLoginSheet by remember { mutableStateOf(false) }
    // 找回密码弹窗
    var showRecoverySheet by remember { mutableStateOf(false) }
    // WebView 半屏弹窗（找回密码用）
    var webViewUrl by remember { mutableStateOf<String?>(null) }
    var webViewTitle by remember { mutableStateOf("") }
    val snackbar = LocalSnackbarController.current

    // 锁屏验证启动器
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            showExportDialog = true
        }
    }

    // 收集一次性事件（替代 LaunchedEffect(uiState.error/loginResult/autoLoginResult)）
    // 使用 Channel 确保事件不会被重复消费，避免配置变更后 Snackbar 再次弹出
    LaunchedEffect(Unit) {
        loginViewModel.events.catch { /* 忽略 Channel 异常 */ }.collect { event ->
            when (event) {
                is LoginEvent.Error -> {
                    snackbar.show(event.msg, ToastUtils.Type.ERROR)
                }
                is LoginEvent.LoginSuccess -> {
                    snackbar.show(resources.getString(R.string.login_success), ToastUtils.Type.SUCCESS)
                    delay(1500)
                    onLoginSuccess()
                }
                is LoginEvent.ExportSuccess -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    val clip = ClipData.newPlainText(
                        resources.getString(R.string.login_export_credential),
                        event.encryptedData
                    )
                    clipboard.setPrimaryClip(clip)
                    snackbar.show(resources.getString(R.string.login_credential_copied), ToastUtils.Type.SUCCESS)
                    showExportDialog = false
                }
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.login_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                // 安全说明 + 三点菜单
                actions = {
                    // 语言切换图标
                    LanguageSwitchButton()

                    // 安全说明问号按钮
                    IconButton(onClick = { showSecurityNotice = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = stringResource(R.string.login_security_notice),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // 三点菜单
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.common_more_options),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AppScaledDropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            val authTitle = stringResource(R.string.login_device_auth_title)
                            val authDesc = stringResource(R.string.login_device_auth_desc)
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.login_export_credential)) },
                                leadingIcon = { Icon(Icons.Outlined.UploadFile, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                                    @Suppress("DEPRECATION")
                                    val deviceSecure = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        km.isDeviceSecure
                                    } else {
                                        km.isKeyguardSecure
                                    }
                                    if (deviceSecure) {
                                        @Suppress("DEPRECATION")
                                        val intent = km.createConfirmDeviceCredentialIntent(
                                            authTitle,
                                            authDesc
                                        )
                                        authLauncher.launch(intent)
                                    } else {
                                        showExportDialog = true
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.login_import_credential)) },
                                leadingIcon = { Icon(Icons.Outlined.CloudDownload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showImportDialog = true
                                }
                            )
                        }
                    }
                },
                colors = topBarColors
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ═══ 主体内容区（可伸缩，将底部按钮推至页面底端）═══
                // verticalScroll 确保横屏等小屏幕下内容溢出时可滚动
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                Spacer(modifier = Modifier.height(48.dp))

                // 标题
                Text(
                    text = stringResource(R.string.login_unified_auth),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )


                Spacer(modifier = Modifier.height(32.dp))

                // 学号输入框
                TextField(
                    value = uiState.username,
                    onValueChange = { loginViewModel.updateUsername(it) },
                    label = { Text(stringResource(R.string.login_student_id)) },
                    placeholder = { Text(stringResource(R.string.login_student_id_hint)) },
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 密码输入框（带显隐切换，仅手动输入密码时可用）
                // hasSavedPassword 时显示占位圆点，眼睛按钮禁用防止查看已保存密码
                TextField(
                    value = uiState.password,
                    onValueChange = { loginViewModel.updatePassword(it) },
                    label = { Text(stringResource(R.string.login_password)) },
                    placeholder = { Text(stringResource(R.string.login_password_hint)) },
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    visualTransformation = if (uiState.passwordRevealed)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        // 始终使用 Password keyboardType，防止切换 IME 导致输入法重启
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            loginViewModel.login()
                        }
                    ),
                    trailingIcon = {
                        // 仅在用户实际输入了密码时显示眼睛按钮（已保存密码占位状态下不显示）
                        if (uiState.password.isNotEmpty() && !uiState.hasSavedPassword) {
                            IconButton(onClick = { loginViewModel.togglePasswordRevealed() }) {
                                Icon(
                                    imageVector = if (uiState.passwordRevealed)
                                        Icons.Outlined.VisibilityOff
                                    else
                                        Icons.Outlined.Visibility,
                                    contentDescription = if (uiState.passwordRevealed) stringResource(R.string.login_hide_password) else stringResource(R.string.login_show_password),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 记住密码复选框
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .toggleable(
                            value = uiState.rememberPassword,
                            onValueChange = { loginViewModel.setRememberPassword(it) },
                            role = Role.Checkbox,
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = uiState.rememberPassword,
                        onCheckedChange = null,
                        enabled = !uiState.isLoading,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = stringResource(R.string.login_remember_password),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 登录按钮（无加载动画，文字固定）
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        loginViewModel.login()
                    },
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(stringResource(R.string.login_login_button), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 取消按钮（点击相当于返回）
                TextButton(
                    onClick = onBack,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.common_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                } // 关闭内层 weight Column

                // ═══ 底部固定区：其他登录 + 找回密码 ═══
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showOtherLoginSheet = true },
                        enabled = !uiState.isLoading
                    ) {
                        Text(
                            stringResource(R.string.login_other_login),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Text(
                        text = "|",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )

                    TextButton(
                        onClick = { showRecoverySheet = true },
                        enabled = !uiState.isLoading
                    ) {
                        Text(
                            stringResource(R.string.login_password_recovery),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

        }
    }

    // ========== 登录加载弹窗 ==========
    if (uiState.isLoading) {
        LoadingDialog(message = stringResource(R.string.login_verifying))
    }

    // ========== 安全说明弹窗 ==========
    SecurityNoticeSheet(
        visible = showSecurityNotice,
        onDismiss = { showSecurityNotice = false },
    )

    // ========== 其他登录方式弹窗 ==========
    BottomSheetDialog(
        visible = showOtherLoginSheet,
        onDismissRequest = { showOtherLoginSheet = false },
        title = stringResource(R.string.login_other_login),
    ) {
        BottomSheetItem(
            icon = Icons.Outlined.QrCodeScanner,
            title = stringResource(R.string.login_method_qr_scan),
            onClick = {
                showOtherLoginSheet = false
                nav.navigate(Routes.QR_LOGIN)
            }
        )
        BottomSheetItem(
            icon = Icons.Outlined.Key,
            title = stringResource(R.string.login_method_credential),
            onClick = {
                showOtherLoginSheet = false
                showImportDialog = true
            }
        )
    }

    // ========== 找回密码弹窗 ==========
    val phoneRecoveryTitle = stringResource(R.string.login_method_phone_recovery)
    val emailRecoveryTitle = stringResource(R.string.login_method_email_recovery)
    BottomSheetDialog(
        visible = showRecoverySheet,
        onDismissRequest = { showRecoverySheet = false },
        title = stringResource(R.string.login_password_recovery),
    ) {
        BottomSheetItem(
            icon = Icons.Outlined.Phone,
            title = phoneRecoveryTitle,
            onClick = {
                showRecoverySheet = false
                webViewTitle = phoneRecoveryTitle
                webViewUrl = "https://authserver.cqwu.edu.cn/authserver/mobileGetPasswordController.do"
            }
        )
        BottomSheetItem(
            icon = Icons.Outlined.Email,
            title = emailRecoveryTitle,
            onClick = {
                showRecoverySheet = false
                webViewTitle = emailRecoveryTitle
                webViewUrl = "https://authserver.cqwu.edu.cn/authserver/moblieFindPwdByMailPage.do"
            }
        )
    }

    // ========== 找回密码 WebView 半屏弹窗 ==========
    WebViewBottomSheet(
        visible = webViewUrl != null,
        onDismissRequest = { webViewUrl = null },
        url = webViewUrl ?: "",
        title = webViewTitle
    )

    // ========== 导入凭据对话框 ==========
    ImportCredentialDialog(
        show = showImportDialog,
        onDismiss = { showImportDialog = false },
        onConfirm = { data, password ->
            loginViewModel.importAndLogin(data, password)
            showImportDialog = false
        },
    )

    // ========== 导出凭据对话框 ==========
    ExportCredentialDialog(
        show = showExportDialog,
        currentUsername = uiState.username,
        onDismiss = { showExportDialog = false },
        onConfirm = { password ->
            loginViewModel.exportCredentials(password)
            showExportDialog = false
        },
    )

}
