package edu.cqwu.electricity.ui.login

import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.ui.components.BottomSheetDialog
import edu.cqwu.electricity.ui.components.BottomSheetItem
import edu.cqwu.electricity.ui.components.LanguageSwitchButton
import edu.cqwu.electricity.ui.components.LoadingDialog
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.navigation.Routes
import edu.cqwu.electricity.ui.theme.LocalNavController
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import edu.cqwu.electricity.util.ToastUtils
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
    loginViewModel: LoginViewModel = viewModel()
) {
    val uiState by loginViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val nav = LocalNavController.current

    // 每次页面变为可见时刷新已保存账号列表（例如从扫码登录返回后）
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loginViewModel.refreshSavedAccounts()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)

    // 三点菜单与凭据导入导出
    var showMenu by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    // 安全说明弹窗
    var showSecurityNotice by remember { mutableStateOf(false) }
    // 其他登录方式弹窗
    var showOtherLoginSheet by remember { mutableStateOf(false) }
    // 学号下拉选择
    var showAccountDropdown by remember { mutableStateOf(false) }
    // 删除账号确认弹窗：记录待删除的学号
    val snackbar = LocalSnackbarController.current
    var deleteConfirmAccount by remember { mutableStateOf<String?>(null) }

    // 锁屏验证启动器
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            showExportDialog = true
        }
    }

    // 拦截系统返回（包括侧滑手势和物理返回键）
    // 加载中/自动切换中拦截，防止登录请求中误触退出导致状态丢失
    BackHandler(enabled = uiState.isLoading || uiState.isAutoSwitching) {
        snackbar.show(context.getString(R.string.login_verifying), ToastUtils.Type.ERROR)
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
                    snackbar.show(context.getString(R.string.login_success), ToastUtils.Type.SUCCESS)
                    delay(1500)
                    onBack()
                }
                is LoginEvent.AutoSwitchSuccess -> {
                    android.util.Log.d("LoginScreen", "智能切换成功: 用户[${event.username}] 已自动切换 Cookie 环境")
                    delay(500)
                    onBack()
                }
                is LoginEvent.ExportSuccess -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    val clip = ClipData.newPlainText(
                        context.getString(R.string.login_export_credential),
                        event.encryptedData
                    )
                    clipboard.setPrimaryClip(clip)
                    snackbar.show(context.getString(R.string.login_credential_copied), ToastUtils.Type.SUCCESS)
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = stringResource(R.string.login_security_notice),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // 三点菜单
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.common_more_options),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            val authTitle = stringResource(R.string.login_device_auth_title)
                            val authDesc = stringResource(R.string.login_device_auth_desc)
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.login_export_credential)) },
                                leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                                    if (km.isDeviceSecure) {
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
                                leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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

                // 学号输入框（带下拉选择已有用户）
                Box {
                    TextField(
                        value = uiState.username,
                        onValueChange = { loginViewModel.updateUsername(it) },
                        label = { Text(stringResource(R.string.login_student_id)) },
                        placeholder = { Text(stringResource(R.string.login_student_id_hint)) },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
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
                        trailingIcon = {
                            if (uiState.savedAccounts.isNotEmpty()) {
                                IconButton(onClick = { showAccountDropdown = !showAccountDropdown }) {
                                    Icon(
                                        imageVector = if (showAccountDropdown)
                                            Icons.Default.KeyboardArrowUp
                                        else
                                            Icons.Default.ArrowDropDown,
                                        contentDescription = stringResource(R.string.common_select_saved_user)
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

                    // 学号下拉选择菜单（类似QQ样式：账号居左，删除按钮居右）
                    AccountDropdownMenu(
                        savedAccounts = uiState.savedAccounts,
                        expanded = showAccountDropdown,
                        onDismiss = { showAccountDropdown = false },
                        onSelectAccount = { loginViewModel.switchToUser(it) },
                        onDeleteAccount = { deleteConfirmAccount = it },
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 密码输入框（带显隐切换，仅手动输入密码时可用）
                TextField(
                    value = uiState.password,
                    onValueChange = { loginViewModel.updatePassword(it) },
                    label = { Text(stringResource(R.string.login_password)) },
                    placeholder = { Text(stringResource(R.string.login_password_hint)) },
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
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
                        // 仅用户手动输入的密码才可切换显示
                        if (!uiState.passwordFromStorage && uiState.password.isNotEmpty()) {
                            IconButton(onClick = { loginViewModel.togglePasswordRevealed() }) {
                                Icon(
                                    imageVector = if (uiState.passwordRevealed)
                                        Icons.Default.VisibilityOff
                                    else
                                        Icons.Default.Visibility,
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

                // ═══ 底部固定区：扫码登录 | 添加账号 ═══
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )

                    TextButton(
                        onClick = {
                            loginViewModel.updateUsername("")
                            loginViewModel.updatePassword("")
                        },
                        enabled = !uiState.isLoading
                    ) {
                        Text(
                            stringResource(R.string.login_add_account),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

        }
    }

    // ========== 登录/切换账号加载弹窗 ==========
    val showLoading = uiState.isLoading || uiState.isAutoSwitching
    if (showLoading) {
        LoadingDialog(
            message = if (uiState.isAutoSwitching)
                stringResource(R.string.login_auto_switching)
            else
                stringResource(R.string.login_verifying)
        )
    }

    // ========== 安全说明弹窗 ==========
    if (showSecurityNotice) {
        SecurityNoticeSheet(
            onDismiss = { showSecurityNotice = false },
        )
    }

    // ========== 其他登录方式弹窗 ==========
    if (showOtherLoginSheet) {
        val phoneRecoveryTitle = stringResource(R.string.login_method_phone_recovery)
        val emailRecoveryTitle = stringResource(R.string.login_method_email_recovery)
        BottomSheetDialog(
            onDismissRequest = { showOtherLoginSheet = false },
            title = stringResource(R.string.login_other_login),
        ) {
            BottomSheetItem(
                icon = Icons.Default.QrCodeScanner,
                title = stringResource(R.string.login_method_qr_scan),
                onClick = {
                    showOtherLoginSheet = false
                    nav.navigate(Routes.QR_LOGIN)
                }
            )
            BottomSheetItem(
                icon = Icons.Default.Key,
                title = stringResource(R.string.login_method_credential),
                onClick = {
                    showOtherLoginSheet = false
                    showImportDialog = true
                }
            )
            BottomSheetItem(
                icon = Icons.Default.Phone,
                title = phoneRecoveryTitle,
                onClick = {
                    showOtherLoginSheet = false
                    nav.navigate(Routes.unifiedWebViewRoute(
                        "https://authserver.cqwu.edu.cn/authserver/mobileGetPasswordController.do",
                        phoneRecoveryTitle
                    ))
                }
            )
            BottomSheetItem(
                icon = Icons.Default.Email,
                title = emailRecoveryTitle,
                onClick = {
                    showOtherLoginSheet = false
                    nav.navigate(Routes.unifiedWebViewRoute(
                        "https://authserver.cqwu.edu.cn/authserver/moblieFindPwdByMailPage.do",
                        emailRecoveryTitle
                    ))
                }
            )
        }
    }

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

    // ── 删除账号确认弹窗 ──
    DeleteAccountSheet(
        account = deleteConfirmAccount,
        onDismiss = { deleteConfirmAccount = null },
        onConfirm = {
            loginViewModel.removeAccount(deleteConfirmAccount!!)
            deleteConfirmAccount = null
            showAccountDropdown = false
        },
    )

}
