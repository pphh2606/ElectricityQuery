package edu.cqwu.electricity.settings.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.login.domain.SessionCoordinatorV2
import edu.cqwu.electricity.theme.ui.LocalNavController
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import edu.cqwu.electricity.theme.util.ToastUtils

/**
 * 备份与恢复子页：列出各类备份能力（设置项 / 登录 Cookie / 登录凭据）。
 *
 * 三类统一交互：点条目 → 带拖拽手柄的选择弹窗（导出 / 导入）→ 各自的流程。
 * 登录凭据（账号+密码，加密文件）导出前要求系统锁屏确认，导入只存为新账号、不激活。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
) {
    val nav = LocalNavController.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbarController.current
    val topBarColors = currentTopBarColors()
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showCookieSheet by remember { mutableStateOf(false) }
    var showCredentialSheet by remember { mutableStateOf(false) }
    var showCredentialExportDialog by remember { mutableStateOf(false) }
    var showCredentialImportDialog by remember { mutableStateOf(false) }
    val credentialViewModel: CredentialBackupViewModel = viewModel()

    // 锁屏验证（导出登录凭据前要求系统锁屏确认，与登录页原行为一致）
    val authTitle = stringResource(R.string.login_device_auth_title)
    val authDesc = stringResource(R.string.login_device_auth_desc)
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            showCredentialExportDialog = true
        }
    }

    // 登录凭据导出/导入结果提示
    LaunchedEffect(Unit) {
        credentialViewModel.events.collect { event ->
            when (event) {
                is CredentialTransferEvent.ExportDone ->
                    snackbar.show(resources.getString(R.string.login_credential_copied), ToastUtils.Type.SUCCESS)

                is CredentialTransferEvent.ImportDone ->
                    snackbar.show(
                        resources.getString(R.string.settings_credential_import_done, event.count),
                        ToastUtils.Type.SUCCESS,
                    )

                is CredentialTransferEvent.Error ->
                    snackbar.show(event.message, ToastUtils.Type.ERROR)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_backup_restore),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = topBarColors,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    SettingsEntry(
                        icon = Icons.Outlined.Backup,
                        title = stringResource(R.string.settings_backup_settings_item),
                        onClick = { showSettingsSheet = true },
                    )
                    SettingsEntry(
                        icon = Icons.Outlined.Lock,
                        title = stringResource(R.string.settings_cookie_item),
                        onClick = { showCookieSheet = true },
                    )
                    SettingsEntry(
                        icon = Icons.Outlined.Person,
                        title = stringResource(R.string.settings_credential_item),
                        onClick = { showCredentialSheet = true },
                    )
                }
            }
        }
    }

    // 设置项备份选择弹窗
    BackupRestoreSheet(
        visible = showSettingsSheet,
        onDismiss = { showSettingsSheet = false },
        onExport = {
            showSettingsSheet = false
            nav.navigate(Routes.SETTINGS_BACKUP_EXPORT)
        },
        onImport = {
            showSettingsSheet = false
            nav.navigate(Routes.SETTINGS_BACKUP_IMPORT)
        },
    )
    // Cookie 备份选择弹窗
    BackupRestoreSheet(
        visible = showCookieSheet,
        onDismiss = { showCookieSheet = false },
        onExport = {
            showCookieSheet = false
            nav.navigate(Routes.SETTINGS_COOKIE_EXPORT)
        },
        onImport = {
            showCookieSheet = false
            nav.navigate(Routes.SETTINGS_COOKIE_IMPORT)
        },
        exportLabelRes = R.string.settings_cookie_sheet_export,
        importLabelRes = R.string.settings_cookie_sheet_import,
    )
    // 登录凭据选择弹窗
    BackupRestoreSheet(
        visible = showCredentialSheet,
        onDismiss = { showCredentialSheet = false },
        onExport = {
            showCredentialSheet = false
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            @Suppress("DEPRECATION")
            val deviceSecure = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                km.isDeviceSecure
            } else {
                km.isKeyguardSecure
            }
            if (deviceSecure) {
                @Suppress("DEPRECATION")
                val intent = km.createConfirmDeviceCredentialIntent(authTitle, authDesc)
                authLauncher.launch(intent)
            } else {
                showCredentialExportDialog = true
            }
        },
        onImport = {
            showCredentialSheet = false
            showCredentialImportDialog = true
        },
        exportLabelRes = R.string.settings_credential_sheet_export,
        importLabelRes = R.string.settings_credential_sheet_import,
    )

    // ========== 登录凭据导入对话框 ==========
    ImportCredentialDialog(
        show = showCredentialImportDialog,
        onDismiss = { showCredentialImportDialog = false },
        onConfirm = { data, password ->
            credentialViewModel.importCredentials(data, password)
            showCredentialImportDialog = false
        },
    )

    // ========== 登录凭据导出对话框 ==========
    ExportCredentialDialog(
        show = showCredentialExportDialog,
        currentUsername = SessionCoordinatorV2.currentAccount()?.username.orEmpty(),
        onDismiss = { showCredentialExportDialog = false },
        onConfirm = { password ->
            credentialViewModel.exportCredentials(password)
            showCredentialExportDialog = false
        },
    )
}
