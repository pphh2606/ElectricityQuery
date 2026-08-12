package edu.cqwu.electricity.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.LocalTopBarState
import edu.cqwu.electricity.theme.ui.toTopAppBarColors
import edu.cqwu.electricity.settings.util.StorageManager
import edu.cqwu.electricity.theme.util.ToastUtils
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
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
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
                key = "temp_logs",
                icon = Icons.Outlined.Description,
                titleRes = R.string.storage_clear_temp_logs,
                isSafe = true,
                getSize = { getTempLogSize() },
                clear = { clearTempLogs() },
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
                // 重新计算大小
                val result = withContext(Dispatchers.IO) {
                    items.associate { it.key to StorageManager.formatSize(it.getSize(storageManager)) }
                }
                sizes.clear()
                sizes.putAll(result)
                snackbar.show(resources.getString(R.string.storage_clear_success), ToastUtils.Type.SUCCESS)
            } catch (e: Exception) {
                snackbar.show(resources.getString(R.string.common_clear_failed, e.message ?: ""), ToastUtils.Type.ERROR)
            } finally {
                isClearing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.storage_clear_title),
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
        PullToRefreshBox(
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
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                // ── 安全清除 ──
                SectionHeader(title = stringResource(R.string.storage_clear_safe_group))
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        safeItems.forEach { item ->
                            StorageItemRow(
                                icon = item.icon,
                                title = stringResource(item.titleRes),
                                size = sizes[item.key] ?: stringResource(R.string.storage_clear_zero_size),
                                checked = checked[item.key] == true,
                                onCheckedChange = { checked[item.key] = it },
                                isClearing = isClearing,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── 需谨慎清除 ──
                SectionHeader(title = stringResource(R.string.storage_clear_caution_group))
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        // 构建反向依赖映射：key -> 依赖它的项列表
                        val reverseDeps = remember(cautionItems) {
                            cautionItems
                                .filter { it.requiresLinkedKey != null }
                                .groupBy { it.requiresLinkedKey!! }
                                .mapValues { (_, v) -> v.map { it.key } }
                        }

                        cautionItems.forEach { item ->
                            StorageItemRow(
                                icon = item.icon,
                                title = stringResource(item.titleRes),
                                hint = item.hintRes?.let { stringResource(it) },
                                size = sizes[item.key] ?: stringResource(R.string.storage_clear_zero_size),
                                checked = checked[item.key] == true,
                                onCheckedChange = { newValue ->
                                    checked[item.key] = newValue
                                    if (newValue) {
                                        // 正向联动：勾选时强制勾选依赖项
                                        if (item.requiresLinkedKey != null) {
                                            checked[item.requiresLinkedKey] = true
                                        }
                                    } else {
                                        // 反向联动：取消勾选时，取消所有依赖此项的项
                                        reverseDeps[item.key]?.forEach { depKey ->
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
                    onClick = {
                        if (!hasSelection) {
                            snackbar.show(
                                resources.getString(R.string.storage_clear_nothing_selected),
                                ToastUtils.Type.ERROR,
                            )
                            return@Button
                        }
                        if (hasCautionSelected()) {
                            showConfirmDialog = true
                        } else {
                            doClear()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasSelection && !isClearing,
                    shape = RoundedCornerShape(12.dp),
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
        } // PullToRefreshBox
    }

    // ── 确认弹窗（Android 2.x 旧式风格，更醒目） ──
    if (showConfirmDialog) {
        val selectedNames = getSelectedNames().joinToString("\n• ", prefix = "• ")
        val confirmTitle = stringResource(R.string.storage_clear_confirm_title)
        val confirmMessage = stringResource(R.string.storage_clear_confirm_message, selectedNames)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isClearing) { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                )
            }
        }

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
}
