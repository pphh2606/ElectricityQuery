package edu.cqwu.electricity.ui.electricity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.data.model.CurrentDataResponse
import edu.cqwu.electricity.data.model.DetailType
import edu.cqwu.electricity.data.model.HourDataRecord
import edu.cqwu.electricity.data.model.MeterDataItem
import edu.cqwu.electricity.data.model.UsageRecord
import edu.cqwu.electricity.data.model.UsageResponse
import edu.cqwu.electricity.ui.electricity.DetailViewModel
import edu.cqwu.electricity.util.ToastUtils

// 三点菜单
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

// 剪贴板与文件导出
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.components.SnackbarController
import java.io.OutputStream

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
        DetailType.SIX_MONTH_USAGE -> "最近6个月用电记录"
        DetailType.MONTH_DAILY_USAGE -> "本月每日用电"
        DetailType.HOURLY_USAGE -> "近24h用电明细"
        DetailType.METER_STATUS -> "电表实时状态"
    }

    // 控制三点菜单
    var showMenu by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbarController.current
    val context = LocalContext.current

    // 文件导出启动器
    var pendingExportText by remember { mutableStateOf("") }
    var pendingExportLabel by remember { mutableStateOf("") }
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && pendingExportText.isNotEmpty()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pendingExportText.toByteArray(Charsets.UTF_8))
                }
                snackbar.show("已导出到文件: $pendingExportLabel", ToastUtils.Type.SUCCESS)
            } catch (e: Exception) {
                snackbar.show("导出失败: ${e.message}", ToastUtils.Type.ERROR)
            }
            pendingExportText = ""
            pendingExportLabel = ""
        }
    }

    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
    Box(Modifier.fillMaxSize()) {
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多选项",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("复制") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val text = getDetailTextContent(detailType, detailState)
                                    copyToClipboard(context, text, title, snackbar)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导出") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    pendingExportText = getDetailTextContent(detailType, detailState)
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
                                text = detailState.error ?: "未知错误",
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
}

// ============================================================
//  最近6个月用电记录
// ============================================================

@Composable
private fun SixMonthUsageContent(data: UsageResponse?) {
    val records = data?.costObj
    if (records.isNullOrEmpty()) {
        EmptyPlaceholder("暂无用电记录")
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SectionTitle("最近6个月用电记录")
        }

        items(records) { record ->
            UsageRecordCard(record)
        }
    }
}

// ============================================================
//  本月每日用电
// ============================================================

@Composable
private fun MonthDailyUsageContent(data: UsageResponse?) {
    val records = data?.costObj
    if (records.isNullOrEmpty()) {
        EmptyPlaceholder("暂无每日用电数据")
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SectionTitle("本月每日用电明细")
        }

        items(records) { record ->
            UsageRecordCard(record)
        }
    }
}

// ============================================================
//  近24h用电明细（从 currentData.hourDataObj 读取）
// ============================================================

@Composable
private fun HourlyUsageContent(data: CurrentDataResponse?) {
    val records = data?.hourDataObj
    if (records.isNullOrEmpty()) {
        EmptyPlaceholder("暂无24h用电数据")
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SectionTitle("近24h用电明细")
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
    if (data == null) {
        EmptyPlaceholder("暂无电表数据")
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle("电表实时状态")
        }

        // 电流 (exp4)
        val currentItems = data.exp4 ?: emptyList()
        if (currentItems.isNotEmpty()) {
            item {
                MeterGroupCard("电流", currentItems, "A")
            }
        }

        // 电压 (exp3)
        val voltageItems = data.exp3 ?: emptyList()
        if (voltageItems.isNotEmpty()) {
            item {
                MeterGroupCard("电压", voltageItems, "V")
            }
        }

        // 当前功率/累计值 (exp2)
        if (!data.exp2.isNullOrBlank()) {
            item {
                SimpleValueCard("当前功率/累计值", data.exp2)
            }
        }

        // 电源状态 (exp5)
        if (!data.exp5.isNullOrBlank()) {
            item {
                SimpleValueCard("电源状态", data.exp5)
            }
        }

        // 全部为空
        if (currentItems.isEmpty() && voltageItems.isEmpty()
            && data.exp2.isNullOrBlank() && data.exp5.isNullOrBlank()) {
            item {
                EmptyPlaceholder("电表详细数据为空")
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
                text = record.costTime ?: "未知时间",
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
                    text = "用电量: ${String.format("%.2f", record.consumeTotal ?: 0.0)} 度",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "费用: ${String.format("%.2f", record.costTotal ?: 0.0)} 元",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                text = record.dataTime ?: "未知时间",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        supportingContent = {
            Text(
                text = "用电量: ${String.format("%.2f", record.dataTotal ?: 0.0)} 度",
                style = MaterialTheme.typography.bodySmall
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
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
                    text = "${String.format("%.3f", item.display)} $unit",
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
 * 根据详情类型和详情状态，生成格式化的纯文本内容（用于复制和导出）
 */
private fun getDetailTextContent(detailType: DetailType, detailState: DetailState): String {
    val sb = StringBuilder()

    when (detailType) {
        DetailType.SIX_MONTH_USAGE -> {
            sb.appendLine("最近6个月用电记录")
            sb.appendLine("=".repeat(40))
            sb.appendLine(String.format("%-20s %-10s %-10s", "时间", "用电量(度)", "费用(元)"))
            sb.appendLine("-".repeat(40))
            detailState.sixMonthUsage?.costObj?.forEach { record ->
                sb.appendLine(
                    String.format("%-20s %-10.2f %-10.2f",
                        record.costTime ?: "未知",
                        record.consumeTotal ?: 0.0,
                        record.costTotal ?: 0.0)
                )
            }
        }

        DetailType.MONTH_DAILY_USAGE -> {
            sb.appendLine("本月每日用电")
            sb.appendLine("=".repeat(40))
            sb.appendLine(String.format("%-20s %-10s %-10s", "时间", "用电量(度)", "费用(元)"))
            sb.appendLine("-".repeat(40))
            detailState.monthDailyUsage?.costObj?.forEach { record ->
                sb.appendLine(
                    String.format("%-20s %-10.2f %-10.2f",
                        record.costTime ?: "未知",
                        record.consumeTotal ?: 0.0,
                        record.costTotal ?: 0.0)
                )
            }
        }

        DetailType.HOURLY_USAGE -> {
            sb.appendLine("近24h用电明细")
            sb.appendLine("=".repeat(40))
            sb.appendLine(String.format("%-20s %-10s", "时间", "用电量(度)"))
            sb.appendLine("-".repeat(30))
            detailState.currentData?.hourDataObj?.forEach { record ->
                sb.appendLine(
                    String.format("%-20s %-10.2f",
                        record.dataTime ?: "未知",
                        record.dataTotal ?: 0.0)
                )
            }
        }

        DetailType.METER_STATUS -> {
            sb.appendLine("电表实时状态")
            sb.appendLine("=".repeat(40))

            // 电流
            val currentItems = detailState.currentData?.exp4
            if (!currentItems.isNullOrEmpty()) {
                sb.appendLine("\n【电流】")
                currentItems.forEach { item ->
                    sb.appendLine(String.format("  %s: %.3f A", item.name, item.display))
                }
            }

            // 电压
            val voltageItems = detailState.currentData?.exp3
            if (!voltageItems.isNullOrEmpty()) {
                sb.appendLine("\n【电压】")
                voltageItems.forEach { item ->
                    sb.appendLine(String.format("  %s: %.3f V", item.name, item.display))
                }
            }

            // 功率/累计值
            if (!detailState.currentData?.exp2.isNullOrBlank()) {
                sb.appendLine("\n【当前功率/累计值】")
                sb.appendLine("  ${detailState.currentData?.exp2}")
            }

            // 电源状态
            if (!detailState.currentData?.exp5.isNullOrBlank()) {
                sb.appendLine("\n【电源状态】")
                sb.appendLine("  ${detailState.currentData?.exp5}")
            }

            if (currentItems.isNullOrEmpty() && voltageItems.isNullOrEmpty()
                && detailState.currentData?.exp2.isNullOrBlank() && detailState.currentData?.exp5.isNullOrBlank()) {
                sb.appendLine("暂无电表数据")
            }
        }
    }

    return sb.toString()
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .shadow(elevation = 2.dp, shape = MaterialTheme.shapes.medium)
    )
}
