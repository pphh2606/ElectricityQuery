package edu.cqwu.electricity.electricity.ui

import edu.cqwu.electricity.theme.ui.currentTopBarColors

import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R
import android.content.res.Resources
import edu.cqwu.electricity.theme.ui.resolve

// 三点菜单

// 剪贴板与文件导出
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import edu.cqwu.electricity.common.ui.AppScaledDropdownMenu
import edu.cqwu.electricity.common.ui.InfoRow
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import edu.cqwu.electricity.electricity.data.CurrentDataResponse
import edu.cqwu.electricity.electricity.data.DetailType
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.util.ToastUtils

/**
 * 详情展示页面
 * 根据 detailType 显示不同的电费详情信息
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    detailType: DetailType,
    onBack: () -> Unit
) {
    val detailState by viewModel.detailState.collectAsState()

    // 进入页面时自动加载数据
    LaunchedEffect(detailType) {
        viewModel.loadCurrentData()
    }

    // Composable 退出时自动清除详情数据，避免下次进入时显示旧数据
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearDetailData()
        }
    }

    val title = stringResource(R.string.detail_title_meter)

    // 控制三点菜单
    var showMenu by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbarController.current
    val context = LocalContext.current
    val resources = LocalResources.current

    // 2.3: 使用 rememberSaveable 持久化配置变更
    var pendingExportText by rememberSaveable { mutableStateOf("") }
    var pendingExportLabel by rememberSaveable { mutableStateOf("") }
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && pendingExportText.isNotEmpty()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pendingExportText.toByteArray(Charsets.UTF_8))
                }
                snackbar.show(resources.getString(R.string.common_export_success, pendingExportLabel), ToastUtils.Type.SUCCESS)
            } catch (e: Exception) {
                snackbar.show(resources.getString(R.string.common_export_failed, e.message ?: ""), ToastUtils.Type.ERROR)
            }
            pendingExportText = ""
            pendingExportLabel = ""
        }
    }

    val topBarColors = currentTopBarColors()
    // 2.8: 移除冗余的 Box 包裹
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearDetailData()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
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
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_copy)) },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val text = getDetailTextContent(detailType, detailState, resources)
                                    copyToClipboard(context, text, title, snackbar)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_export)) },
                                leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    pendingExportText = getDetailTextContent(detailType, detailState, resources)
                                    pendingExportLabel = title
                                    saveFileLauncher.launch("electricity_detail.txt")
                                }
                            )
                        }
                    }
                },
                colors = topBarColors
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = detailState.isRefreshing,
            onRefresh = {
                viewModel.loadCurrentData()
            },
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when {
                    detailState.error != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = detailState.error?.resolve(resources) ?: stringResource(R.string.common_unknown_error),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
    
                    else -> {
                        MeterStatusContent(detailState.currentData)
                    }
                }
            }
        }
    }
}

// ============================================================
//  电表实时状态
// ============================================================

@Composable
private fun MeterStatusContent(data: CurrentDataResponse?) {
    // 2.4: 提前判断空状态，避免列表先渲染标题再显示空提示
    val currentItems = data?.exp4 ?: emptyList()
    val voltageItems = data?.exp3 ?: emptyList()
    if (currentItems.isEmpty() && voltageItems.isEmpty()
        && data?.exp2.isNullOrBlank() && data?.exp5.isNullOrBlank()) {
        EmptyPlaceholder(stringResource(R.string.detail_empty_meter))
        return
    }

    // 统一为"左标签 — 右值"的紧凑横向列表，白底平铺，行间细分隔线（同"我的信息"）
    val powerLabel = stringResource(R.string.detail_power_cumulative)
    val statusLabel = stringResource(R.string.detail_power_status)
    val rows = buildList {
        currentItems.forEach { add(it.name to "${String.format(Locale.US, "%.3f", it.display)} A") }
        voltageItems.forEach { add(it.name to "${String.format(Locale.US, "%.3f", it.display)} V") }
        if (!data?.exp2.isNullOrBlank()) add(powerLabel to "${data.exp2} kWh")
        if (!data?.exp5.isNullOrBlank()) add(statusLabel to data.exp5)
    }

    LazyColumn {
        item {
            Column {
                rows.forEachIndexed { index, (label, value) ->
                    InfoRow(
                        label = label,
                        value = value,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                    )
                    if (index < rows.size - 1) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
//  可复用子组件
// ============================================================

@Composable
private fun EmptyPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================
//  三点菜单辅助函数（复制 / 导出）
// ============================================================

/**
 * 2.2: 根据详情类型和详情状态，生成格式化的纯文本内容（用于复制和导出）。
 * 提取通用表格生成逻辑，减少冗余。
 */
private fun getDetailTextContent(detailType: DetailType, detailState: DetailState, resources: Resources): String {
    return buildString {
        appendLine(getDetailTitle(detailType, resources))
        appendLine("=".repeat(40))
        appendMeterStatusText(detailState, resources)
    }
}

/**
 * 获取详情标题。
 */
private fun getDetailTitle(detailType: DetailType, resources: Resources): String =
    resources.getString(R.string.detail_title_meter)

/**
 * 生成电表状态的纯文本内容。
 */
private fun StringBuilder.appendMeterStatusText(detailState: DetailState, resources: Resources) {
    val data = detailState.currentData ?: return

    // 电流
    val currentItems = data.exp4
    if (!currentItems.isNullOrEmpty()) {
        appendLine("\n" + resources.getString(R.string.detail_export_section_current))
        currentItems.forEach { item ->
            appendLine(String.format(Locale.US, "  %s: %.3f A", item.name, item.display))
        }
    }

    // 电压
    val voltageItems = data.exp3
    if (!voltageItems.isNullOrEmpty()) {
        appendLine("\n" + resources.getString(R.string.detail_export_section_voltage))
        voltageItems.forEach { item ->
            appendLine(String.format(Locale.US, "  %s: %.3f V", item.name, item.display))
        }
    }

    // 总用电量
    if (!data.exp2.isNullOrBlank()) {
        appendLine("\n" + resources.getString(R.string.detail_export_section_power))
        appendLine("  ${data.exp2} kWh")
    }

    // 电源状态
    if (!data.exp5.isNullOrBlank()) {
        appendLine("\n" + resources.getString(R.string.detail_export_section_power_status))
        appendLine("  ${data.exp5}")
    }

    if (currentItems.isNullOrEmpty() && voltageItems.isNullOrEmpty()
        && data.exp2.isNullOrBlank() && data.exp5.isNullOrBlank()) {
        appendLine(resources.getString(R.string.detail_export_no_meter_data))
    }
}
