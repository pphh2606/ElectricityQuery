package edu.cqwu.electricity.electricity.ui

import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R
import android.content.res.Resources
import edu.cqwu.electricity.theme.ui.resolve

// 三点菜单

// 剪贴板与文件导出
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import edu.cqwu.electricity.electricity.data.CurrentDataResponse
import edu.cqwu.electricity.electricity.data.DetailType
import edu.cqwu.electricity.electricity.data.HourDataRecord
import edu.cqwu.electricity.electricity.data.MeterDataItem
import edu.cqwu.electricity.electricity.data.UsageRecord
import edu.cqwu.electricity.electricity.data.UsageResponse
import edu.cqwu.electricity.theme.ui.ElectricityLineChartCard
import edu.cqwu.electricity.theme.ui.LineData
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.LocalTopBarState
import edu.cqwu.electricity.theme.ui.toTopAppBarColors
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
        when (detailType) {
            DetailType.SIX_MONTH_USAGE -> viewModel.loadSixMonthUsage()
            DetailType.MONTH_DAILY_USAGE -> viewModel.loadMonthDailyUsage()
            DetailType.HOURLY_USAGE -> viewModel.loadCurrentData()
            DetailType.METER_STATUS -> viewModel.loadCurrentData()
        }
    }

    // Composable 退出时自动清除详情数据，避免下次进入时显示旧数据
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearDetailData()
        }
    }

    val title = when (detailType) {
        DetailType.SIX_MONTH_USAGE -> stringResource(R.string.detail_title_6months)
        DetailType.MONTH_DAILY_USAGE -> stringResource(R.string.detail_title_daily)
        DetailType.HOURLY_USAGE -> stringResource(R.string.detail_title_hourly)
        DetailType.METER_STATUS -> stringResource(R.string.detail_title_meter)
    }

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

    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
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
                        DropdownMenu(
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
                when (detailType) {
                    DetailType.SIX_MONTH_USAGE -> viewModel.loadSixMonthUsage()
                    DetailType.MONTH_DAILY_USAGE -> viewModel.loadMonthDailyUsage()
                    DetailType.HOURLY_USAGE -> viewModel.loadCurrentData()
                    DetailType.METER_STATUS -> viewModel.loadCurrentData()
                }
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
                        when (detailType) {
                            DetailType.SIX_MONTH_USAGE -> {
                                SixMonthUsageContent(detailState.sixMonthUsage)
                            }
                            DetailType.MONTH_DAILY_USAGE -> {
                                MonthDailyUsageContent(detailState.monthDailyUsage)
                            }
                            DetailType.HOURLY_USAGE -> {
                                HourlyUsageContent(detailState.currentData)
                            }
                            DetailType.METER_STATUS -> {
                                MeterStatusContent(detailState.currentData)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  2.6: 统一 Usage 列表 + 折线图组件（合并 SixMonthUsageContent 与 MonthDailyUsageContent）
//       两者唯一区别是标题、X 轴标签格式、空提示文案
// ============================================================

/**
 * 通用的用电记录列表（含顶部折线图）。
 *
 * @param data 用电数据
 * @param emptyMessage 空数据提示文案
 * @param xLabelTransform 将 [UsageRecord] 转换为 X 轴标签的函数
 */
@Composable
private fun UsageListWithChart(
    data: UsageResponse?,
    emptyMessage: String,
    xLabelTransform: (UsageRecord) -> String
) {
    val records = data?.costObj
    if (records.isNullOrEmpty()) {
        EmptyPlaceholder(emptyMessage)
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 折线图
        item(key = "chart") {
            ElectricityLineChartCard(
                xLabels = records.map(xLabelTransform),
                lines = listOf(
                    LineData(stringResource(R.string.detail_chart_usage_kwh), records.map { it.consumeTotal }, Color(0xFF2196F3)),
                    LineData(stringResource(R.string.detail_chart_cost_yuan), records.map { it.costTotal }, Color(0xFFE53935))
                )
            )
        }

        item {
            SectionTitle(stringResource(R.string.detail_section_data))
        }

        items(records) { record ->
            UsageRecordCard(record)
        }
    }
}

/**
 * 最近6个月用电记录内容（委托给 [UsageListWithChart]）
 */
@Composable
private fun SixMonthUsageContent(data: UsageResponse?) {
    val resources = LocalResources.current
    UsageListWithChart(
        data = data,
        emptyMessage = stringResource(R.string.detail_empty_record),
        xLabelTransform = { it.costTime.takeLast(2) + resources.getString(R.string.detail_month_unit) }
    )
}

/**
 * 本月每日用电内容（委托给 [UsageListWithChart]）
 */
@Composable
private fun MonthDailyUsageContent(data: UsageResponse?) {
    UsageListWithChart(
        data = data,
        emptyMessage = stringResource(R.string.detail_empty_daily),
        xLabelTransform = { it.costTime.takeLast(5) }
    )
}

// ============================================================
//  近24h用电明细（从 currentData.hourDataObj 读取）
// ============================================================

@Composable
private fun HourlyUsageContent(data: CurrentDataResponse?) {
    val records = data?.hourDataObj
    if (records.isNullOrEmpty()) {
        EmptyPlaceholder(stringResource(R.string.detail_empty_24h))
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 折线图（单线：仅用电量）
        item(key = "chart") {
            ElectricityLineChartCard(
                xLabels = records.map { it.dataTime.takeLast(5) },
                lines = listOf(
                    LineData(stringResource(R.string.detail_chart_usage_kwh), records.map { it.dataTotal }, Color(0xFF2196F3))
                )
            )
        }

        item {
            SectionTitle(stringResource(R.string.detail_section_data))
        }

        items(records) { record ->
            HourDataCard(record)
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

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle(stringResource(R.string.detail_section_meter_status))
        }

        // 电流 (exp4)
        if (currentItems.isNotEmpty()) {
            item {
                MeterGroupCard(stringResource(R.string.detail_current), currentItems, "A")
            }
        }

        // 电压 (exp3)
        if (voltageItems.isNotEmpty()) {
            item {
                MeterGroupCard(stringResource(R.string.detail_voltage), voltageItems, "V")
            }
        }

        // 当前功率/累计值 (exp2)
        if (!data?.exp2.isNullOrBlank()) {
            item {
                SimpleValueCard(stringResource(R.string.detail_power_cumulative), data.exp2)
            }
        }

        // 电源状态 (exp5)
        if (!data?.exp5.isNullOrBlank()) {
            item {
                SimpleValueCard(stringResource(R.string.detail_power_status), data.exp5)
            }
        }
    }
}

// ============================================================
//  可复用子组件
// ============================================================

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

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

/**
 * 用电记录卡片（用于6个月和本月每日）
 */
@Composable
private fun UsageRecordCard(record: UsageRecord) {
    ListItem(
        headlineContent = {
            Text(
                text = record.costTime,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        supportingContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.detail_power_consumption, String.format(Locale.US, "%.2f", record.consumeTotal)),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.detail_cost, String.format(Locale.US, "%.2f", record.costTotal)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .shadow(elevation = 2.dp, shape = MaterialTheme.shapes.medium)
    )
}

/**
 * 每小时数据卡片
 */
@Composable
private fun HourDataCard(record: HourDataRecord) {
    ListItem(
        headlineContent = {
            Text(
                text = record.dataTime,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.detail_total_consumption, String.format(Locale.US, "%.2f", record.dataTotal)),
                style = MaterialTheme.typography.bodySmall
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .shadow(elevation = 2.dp, shape = MaterialTheme.shapes.medium)
    )
}

/**
 * 电表参数分组卡片（用于电压/电流等列表数据）
 */
@Composable
private fun MeterGroupCard(groupName: String, items: List<MeterDataItem>, unit: String = "") {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .shadow(elevation = 2.dp, shape = MaterialTheme.shapes.medium)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium
            )
            .padding(12.dp)
    ) {
        Text(
            text = groupName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${String.format(Locale.US, "%.3f", item.display)} $unit",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (index < items.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
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

        when (detailType) {
            DetailType.SIX_MONTH_USAGE,
            DetailType.MONTH_DAILY_USAGE -> {
                val records = when (detailType) {
                    DetailType.SIX_MONTH_USAGE -> detailState.sixMonthUsage?.costObj
                    else -> detailState.monthDailyUsage?.costObj
                }
                appendLine(String.format("%-20s %-10s %-10s", resources.getString(R.string.detail_export_time), resources.getString(R.string.detail_export_usage), resources.getString(R.string.detail_export_cost)))
                appendLine("-".repeat(40))
                records?.forEach { record ->
                    appendLine(
                        String.format(Locale.US, "%-20s %-10.2f %-10.2f",
                            record.costTime,
                            record.consumeTotal,
                            record.costTotal)
                    )
                }
            }

            DetailType.HOURLY_USAGE -> {
                appendLine(String.format("%-20s %-10s", resources.getString(R.string.detail_export_time), resources.getString(R.string.detail_export_usage)))
                appendLine("-".repeat(30))
                detailState.currentData?.hourDataObj?.forEach { record ->
                    appendLine(
                        String.format(Locale.US, "%-20s %-10.2f",
                            record.dataTime,
                            record.dataTotal)
                    )
                }
            }

            DetailType.METER_STATUS -> {
                appendMeterStatusText(detailState, resources)
            }
        }
    }
}

/**
 * 获取详情标题。
 */
private fun getDetailTitle(detailType: DetailType, resources: Resources): String = when (detailType) {
    DetailType.SIX_MONTH_USAGE -> resources.getString(R.string.detail_title_6months)
    DetailType.MONTH_DAILY_USAGE -> resources.getString(R.string.detail_title_daily)
    DetailType.HOURLY_USAGE -> resources.getString(R.string.detail_title_hourly)
    DetailType.METER_STATUS -> resources.getString(R.string.detail_title_meter)
}

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

    // 功率/累计值
    if (!data.exp2.isNullOrBlank()) {
        appendLine("\n" + resources.getString(R.string.detail_export_section_power))
        appendLine("  ${data.exp2}")
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

/**
 * 简单文本值卡片（用于功率/状态等单值数据）
 */
@Composable
private fun SimpleValueCard(label: String, value: String) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Text(
                text = value,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .shadow(elevation = 2.dp, shape = MaterialTheme.shapes.medium)
    )
}
