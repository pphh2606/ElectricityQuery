@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package edu.cqwu.electricity.settings.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import edu.cqwu.electricity.common.ui.AppPullToRefresh

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.ui.CollapsibleScaffold
import edu.cqwu.electricity.common.ui.LoadingDialog
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.settings.util.StorageManager
import edu.cqwu.electricity.common.util.ToastUtils
import edu.cqwu.electricity.common.util.restartApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 存储清理项数据模型
 */
private data class StorageItem(
    val key: String,
    val icon: ImageVector,
    val titleRes: Int,
    val hintRes: Int? = null,
    val isSafe: Boolean,       // true = 安全清除，false = 需谨慎
    val requiresLinkedKey: String? = null,  // 勾选时强制联动勾选的 key
    val getSize: StorageManager.() -> Long,
    val clear: StorageManager.() -> Unit,
)

/**
 * 清除存储空间页面。
 *
 * 原生安卓 Material3 风格，参照系统「存储」设置页的布局。
 * 按安全/风险分两组展示，每项显示勾选框 + 图标 + 名称 + 大小。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageClearScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()

    val storageManager = remember { StorageManager(context) }

    // 存储项定义
    val items = remember {
        listOf(
            StorageItem(
                key = "image_cache",
                icon = Icons.Outlined.Image,
                titleRes = R.string.storage_clear_image_cache,
                isSafe = true,
                getSize = { getCacheImageSize() },
                clear = { clearCacheImages() },
            ),
            StorageItem(
                key = "crash_logs",
                icon = Icons.Outlined.BugReport,
                titleRes = R.string.storage_clear_crash_logs,
                isSafe = true,
                getSize = { getCrashLogSize() },
                clear = { clearCrashLogs() },
            ),
            StorageItem(
                key = "app_logs",
                icon = Icons.Outlined.Description,
                titleRes = R.string.storage_clear_app_logs,
                isSafe = true,
                getSize = { getAppLogSize() },
                clear = { clearAppLogs() },
            ),
            StorageItem(
                key = "webview_data",
                icon = Icons.Outlined.Language,
                titleRes = R.string.storage_clear_webview_data,
                isSafe = true,
                getSize = { getWebViewDataSize() },
                clear = { clearWebViewData() },
            ),
            StorageItem(
                key = "cookie_data",
                icon = Icons.Outlined.Cookie,
                titleRes = R.string.storage_clear_cookie_data,
                hintRes = R.string.storage_clear_cookie_hint,
                isSafe = false,
                getSize = { getCookieDataSize() },
                clear = { clearCookieData() },
            ),
            StorageItem(
                key = "settings",
                icon = Icons.Outlined.Settings,
                titleRes = R.string.storage_clear_settings,
                hintRes = R.string.storage_clear_settings_hint,
                isSafe = false,
                getSize = { getSettingsSize() },
                clear = { clearSettings() },
            ),
            StorageItem(
                key = "account_data",
                icon = Icons.Outlined.AccountCircle,
                titleRes = R.string.storage_clear_account_data,
                hintRes = R.string.storage_clear_account_hint,
                isSafe = false,
                requiresLinkedKey = "cookie_data",
                getSize = { getAccountDataSize() },
                clear = { clearAccountData() },
            ),
        )
    }

    // 各项大小
    val sizes = remember { mutableStateMapOf<String, String>() }
    // 各项勾选状态：安全项默认勾选，风险项默认不勾选
    val checked = remember {
        mutableStateMapOf<String, Boolean>().apply {
            items.forEach { put(it.key, it.isSafe) }
        }
    }
    // 是否正在计算大小（首次加载）
    var isLoading by remember { mutableStateOf(true) }
    // 下拉刷新中
    var isRefreshing by remember { mutableStateOf(false) }
    // 是否正在清除
    var isClearing by remember { mutableStateOf(false) }
    // 确认弹窗
    var showConfirmDialog by remember { mutableStateOf(false) }

    // 重新计算所有存储项大小（首次加载和下拉刷新共用）
    suspend fun reloadSizes() {
        val result = withContext(Dispatchers.IO) {
            items.associate { it.key to StorageManager.formatSize(it.getSize(storageManager)) }
        }
        sizes.clear()
        sizes.putAll(result)
    }

    // 首次加载
    LaunchedEffect(Unit) {
        reloadSizes()
        isLoading = false
    }

    val safeItems = items.filter { it.isSafe }
    val cautionItems = items.filter { !it.isSafe }

    fun getSelectedNames(): List<String> {
        return items.filter { checked[it.key] == true }
            .map { resources.getString(it.titleRes) }
    }

    fun hasCautionSelected(): Boolean {
        return cautionItems.any { checked[it.key] == true }
    }

    fun doClear() {
        isClearing = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    items.filter { checked[it.key] == true }.forEach { it.clear(storageManager) }
                }
                // 清完直接重启；重启后本页重新组合，各项大小自然会重新计算
                restartApp(context)
            } catch (e: Exception) {
                snackbar.show(resources.getString(R.string.common_clear_failed, e.message ?: ""), ToastUtils.Type.ERROR)
                isClearing = false
            }
        }
    }

    CollapsibleScaffold(
        title = stringResource(R.string.storage_clear_title),
        onBack = onBack,
    ) { topInset, scrollModifier ->
        AppPullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    try {
                        reloadSizes()
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            topInset = topInset,
            modifier = Modifier.fillMaxSize(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(scrollModifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(topInset + 8.dp))
            if (isLoading) {
                LoadingDialog(message = stringResource(R.string.common_loading))
            } else {
                // ── 安全清除 ──
                SectionHeader(title = stringResource(R.string.storage_clear_safe_group))
                Spacer(modifier = Modifier.height(8.dp))
                SettingsGroup {
                    safeItems.forEach { entry ->
                        item {
                            StorageItemRow(
                                icon = entry.icon,
                                title = stringResource(entry.titleRes),
                                size = sizes[entry.key] ?: stringResource(R.string.storage_clear_zero_size),
                                checked = checked[entry.key] == true,
                                onCheckedChange = { checked[entry.key] = it },
                                isClearing = isClearing,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── 需谨慎清除 ──
                SectionHeader(title = stringResource(R.string.storage_clear_caution_group))
                Spacer(modifier = Modifier.height(8.dp))
                SettingsGroup {
                    // 构建反向依赖映射：key -> 依赖它的项列表
                    val reverseDeps = remember(cautionItems) {
                        cautionItems
                            .filter { it.requiresLinkedKey != null }
                            .groupBy { it.requiresLinkedKey!! }
                            .mapValues { (_, v) -> v.map { it.key } }
                    }

                    cautionItems.forEach { entry ->
                        item {
                            StorageItemRow(
                                icon = entry.icon,
                                title = stringResource(entry.titleRes),
                                hint = entry.hintRes?.let { stringResource(it) },
                                size = sizes[entry.key] ?: stringResource(R.string.storage_clear_zero_size),
                                checked = checked[entry.key] == true,
                                onCheckedChange = { newValue ->
                                    checked[entry.key] = newValue
                                    if (newValue) {
                                        // 正向联动：勾选时强制勾选依赖项
                                        if (entry.requiresLinkedKey != null) {
                                            checked[entry.requiresLinkedKey] = true
                                        }
                                    } else {
                                        // 反向联动：取消勾选时，取消所有依赖此项的项
                                        reverseDeps[entry.key]?.forEach { depKey ->
                                            checked[depKey] = false
                                        }
                                    }
                                },
                                isClearing = isClearing,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // ── 清除按钮 ──
                val hasSelection = items.any { checked[it.key] == true }
                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        if (!hasSelection) {
                            snackbar.show(
                                resources.getString(R.string.storage_clear_nothing_selected),
                                ToastUtils.Type.ERROR,
                            )
                        } else {
                            showConfirmDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasSelection && !isClearing,
                    colors = if (hasCautionSelected()) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    Text(
                        text = if (isClearing) stringResource(R.string.common_clearing) else stringResource(R.string.storage_clear_button),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        } // AppPullToRefresh
    }

    // ── 确认弹窗（Android 2.x 旧式风格，更醒目） ──
    if (showConfirmDialog) {
        val confirmTitle = stringResource(R.string.storage_clear_confirm_title)
        // 只清安全项时正文仅为一句重启提示；含需谨慎项时列出将被清除的数据
        val confirmMessage = if (!hasCautionSelected()) {
            stringResource(R.string.storage_clear_confirm_restart)
        } else {
            val selectedNames = getSelectedNames().joinToString("\n• ", prefix = "• ")
            stringResource(R.string.storage_clear_confirm_message, selectedNames) +
                "\n\n" + stringResource(R.string.storage_clear_confirm_restart)
        }
        val confirmText = stringResource(R.string.storage_clear_confirm_button)
        val cancelText = stringResource(R.string.storage_clear_cancel_button)

        LaunchedEffect(showConfirmDialog) {
            val dialog = android.app.AlertDialog.Builder(context, android.R.style.Theme_Dialog)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(confirmTitle)
                .setMessage(confirmMessage)
                .setPositiveButton(confirmText) { _, _ ->
                    showConfirmDialog = false
                    doClear()
                }
                .setNegativeButton(cancelText) { _, _ ->
                    showConfirmDialog = false
                }
                .setOnCancelListener {
                    showConfirmDialog = false
                }
                .create()

            dialog.show()
        }
    }
}

/**
 * 分组标题
 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

/**
 * 存储清理项行（原生安卓风格）
 */
@Composable
private fun StorageItemRow(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    size: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isClearing: Boolean,
) {
    SettingsListItem(
        title = title,
        onClick = { onCheckedChange(!checked) },
        icon = icon,
        enabled = !isClearing,
        supporting = hint?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                )
            }
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = size,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onCheckedChange(it) },
                    enabled = !isClearing,
                )
            }
        },
    )
}
