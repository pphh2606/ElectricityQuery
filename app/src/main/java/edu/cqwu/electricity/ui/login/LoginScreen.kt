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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import edu.cqwu.electricity.ui.components.LocalSnackbarController
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
    onNavigateToQrLogin: () -> Unit = {},
    loginViewModel: LoginViewModel = viewModel()
) {
    val uiState by loginViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 导入凭据粘贴框自动聚焦
    val importFocusRequester = remember { FocusRequester() }

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
    // 导出凭据
    var exportPassword by remember { mutableStateOf("") }
    var showExportPassword by remember { mutableStateOf(false) }
    // 导入凭据
    var importDataText by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    // 学号下拉选择
    var showAccountDropdown by remember { mutableStateOf(false) }
    var showImportPassword by remember { mutableStateOf(false) }
    // 删除账号确认弹窗：记录待删除的学号
    val snackbar = LocalSnackbarController.current
    var deleteConfirmAccount by remember { mutableStateOf<String?>(null) }

    // 锁屏验证启动器
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            exportPassword = ""
            showExportPassword = false
            showExportDialog = true
        }
    }

    // 拦截系统返回（包括侧滑手势和物理返回键）
    // 加载中拦截，防止登录请求中误触退出导致状态丢失
    BackHandler(enabled = uiState.isLoading) {
        snackbar.show("正在登录，请稍候...", ToastUtils.Type.ERROR)
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
                    snackbar.show("登录成功!\nCookie: ${event.cookie}", ToastUtils.Type.SUCCESS)
                    delay(1500)
                    onBack()
                }
                is LoginEvent.AutoSwitchSuccess -> {
                    android.util.Log.d("LoginScreen", "智能切换成功: 用户[${event.username}] 已自动切换 Cookie 环境")
                    delay(500)
                    onBack()
                }
            }
        }
    }

    // 导入凭据对话框打开时自动聚焦到粘贴框
    LaunchedEffect(showImportDialog) {
        if (showImportDialog) {
            importFocusRequester.requestFocus()
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
                            DropdownMenuItem(
                                text = { Text("导出凭据") },
                                leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                                    if (km.isDeviceSecure) {
                                        @Suppress("DEPRECATION")
                                        val intent = km.createConfirmDeviceCredentialIntent(
                                            "身份验证",
                                            "验证后导出加密凭据"
                                        )
                                        authLauncher.launch(intent)
                                    } else {
                                        exportPassword = ""
                                        showExportPassword = false
                                        showExportDialog = true
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导入凭据") },
                                leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    importDataText = ""
                                    importPassword = ""
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

                // 下滑刷新风格的加载进度条（仅在登录加载时显示）
                if (uiState.isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 学号输入框（带下拉选择已有用户）
                Box {
                    TextField(
                        value = uiState.username,
                        onValueChange = { loginViewModel.updateUsername(it) },
                        label = { Text("学号") },
                        placeholder = { Text("请输入学号") },
                        singleLine = true,
                        enabled = !uiState.isLoading,
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
                    DropdownMenu(
                        expanded = showAccountDropdown && uiState.savedAccounts.isNotEmpty(),
                        onDismissRequest = { showAccountDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        uiState.savedAccounts.forEach { account ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = account,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                deleteConfirmAccount = account
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.common_delete_account),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    showAccountDropdown = false
                                    loginViewModel.switchToUser(account)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 密码输入框（带显隐切换，仅手动输入密码时可用）
                TextField(
                    value = uiState.password,
                    onValueChange = { loginViewModel.updatePassword(it) },
                    label = { Text("密码") },
                    placeholder = { Text("请输入密码") },
                    singleLine = true,
                    enabled = !uiState.isLoading,
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
                                    contentDescription = if (uiState.passwordRevealed) "隐藏密码" else "显示密码",
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

                Spacer(modifier = Modifier.height(8.dp))

                // 记住密码复选框
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = uiState.rememberPassword,
                        onCheckedChange = { loginViewModel.setRememberPassword(it) },
                        enabled = !uiState.isLoading,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = stringResource(R.string.login_remember_password),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            loginViewModel.setRememberPassword(!uiState.rememberPassword)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                    Text("登  录", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 底部按钮区：扫码登录 | 添加账号
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { onNavigateToQrLogin() },
                        enabled = !uiState.isLoading
                    ) {
                        Text(
                            "扫码登录",
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
                            "添加账号",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

        }
    }

    // ========== 安全说明弹窗 ==========
    if (showSecurityNotice) {
        BottomSheetDialog(
            onDismissRequest = { showSecurityNotice = false },
            title = stringResource(R.string.login_security_notice_title)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.login_security_notice_1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.login_security_notice_2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.login_security_notice_3),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.login_security_notice_4),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.login_security_notice_5),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // ========== 导入凭据对话框 ==========
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入凭据", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.login_paste_credential_label),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.login_paste_credential_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = importDataText,
                        onValueChange = { importDataText = it },
                        placeholder = { Text("在此粘贴加密字符串...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp)
                            .focusRequester(importFocusRequester),
                        maxLines = 4,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = { Text("导出密码") },
                        placeholder = { Text("请输入导出时设置的密码") },
                        singleLine = true,
                        visualTransformation = if (showImportPassword)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showImportPassword = !showImportPassword }) {
                                Icon(
                                    imageVector = if (showImportPassword)
                                        Icons.Default.Visibility
                                    else
                                        Icons.Default.VisibilityOff,
                                    contentDescription = if (showImportPassword) "隐藏密码" else "显示密码"
                                )
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        loginViewModel.importAndLogin(importDataText, importPassword)
                        showImportDialog = false
                    },
                    enabled = importDataText.isNotBlank() && importPassword.isNotBlank()
                ) {
                    Text("导入并登录")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ========== 导出凭据对话框 ==========
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.login_export_credential_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.login_current_account, uiState.username.ifBlank { stringResource(R.string.login_not_entered) }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.login_export_password_label),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it },
                        label = { Text("导出密码") },
                        placeholder = { Text("请设置导出密码，至少 4 位") },
                        singleLine = true,
                        visualTransformation = if (showExportPassword)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showExportPassword = !showExportPassword }) {
                                Icon(
                                    imageVector = if (showExportPassword)
                                        Icons.Default.Visibility
                                    else
                                        Icons.Default.VisibilityOff,
                                    contentDescription = if (showExportPassword) "隐藏密码" else "显示密码"
                                )
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (exportPassword.length < 4) {
                            snackbar.show("密码长度至少 4 位", ToastUtils.Type.ERROR)
                            return@TextButton
                        }
                        when (val result = loginViewModel.exportCredentials(exportPassword)) {
                            is CredentialResult.ExportSuccess -> {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                val clip = ClipData.newPlainText("加密凭据", result.encryptedString)
                                clipboard.setPrimaryClip(clip)
                                snackbar.show("凭据已复制到剪贴板", ToastUtils.Type.SUCCESS)
                            }
                            is CredentialResult.Error -> {
                                snackbar.show(result.message, ToastUtils.Type.ERROR)
                            }
                        }
                    },
                    enabled = exportPassword.isNotBlank()
                ) {
                    Text("复制到剪贴板")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ── 删除账号确认弹窗 ──
    deleteConfirmAccount?.let { account ->
        BottomSheetDialog(
            onDismissRequest = { deleteConfirmAccount = null },
            title = stringResource(R.string.login_delete_account_title),
            icon = Icons.Default.Delete,
            leadingButton = {
                TextButton(onClick = { deleteConfirmAccount = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            trailingButton = {
                TextButton(onClick = {
                    loginViewModel.removeAccount(account)
                    deleteConfirmAccount = null
                    showAccountDropdown = false
                }) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        ) {
            Text(
                text = stringResource(R.string.login_delete_account_confirm, account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

}
