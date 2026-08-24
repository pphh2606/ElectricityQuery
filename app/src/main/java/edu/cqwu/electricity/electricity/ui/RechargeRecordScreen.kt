package edu.cqwu.electricity.electricity.ui

import edu.cqwu.electricity.theme.ui.currentTopBarColors

// 三点菜单

// 剪贴板与文件导出

// 提示弹窗
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import edu.cqwu.electricity.theme.ui.AppScaledDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import edu.cqwu.electricity.theme.ui.AppScaledExposedDropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import android.content.res.Resources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.util.Locale
import edu.cqwu.electricity.R
import edu.cqwu.electricity.electricity.data.BuyRecord
import edu.cqwu.electricity.theme.ui.BottomSheetDialogV2
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.util.ToastUtils

/**
 * 充值记录查询页面
 *
 * UI 布局：
 * - 标题栏：← 返回按钮 + "查询充值记录" + ⋮ 三点菜单（复制/导出）
 * - 查询时间范围下拉菜单（一个月 / 三个月 / 一年 / 四年）
 * - 充值记录结果列表（支持下拉刷新）
 * - 进入页面自动查询指定房间的全部充值记录
 *
 * 与 [RechargeViewModel] 绑定，不依赖 [ElectricityViewModel]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RechargeRecordScreen(
    viewModel: RechargeViewModel,
    roomId: String,
    onBack: () -> Unit
) {
    val recordState by viewModel.recordState.collectAsState()
    val topBarColors = currentTopBarColors()

    // 下拉菜单状态
    var dropdownExpanded by remember { mutableStateOf(false) }

    // 三点菜单状态
    var showMenu by remember { mutableStateOf(false) }

    // 提示弹窗状态
    var showInfoDialog by remember { mutableStateOf(false) }

    val snackbar = LocalSnackbarController.current

    val context = LocalContext.current
    val resources = LocalResources.current

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
                snackbar.show(resources.getString(R.string.common_export_success, pendingExportLabel), ToastUtils.Type.SUCCESS)
            } catch (e: Exception) {
                snackbar.show(resources.getString(R.string.common_export_failed, e.message ?: ""), ToastUtils.Type.ERROR)
            }
            pendingExportText = ""
            pendingExportLabel = ""
        }
    }

    // 进入页面自动查询充值记录，切换时间范围或房间时也自动重新查询
    LaunchedEffect(roomId, recordState.timeRange) {
        viewModel.queryRechargeRecords(roomId)
    }

    // 页面离开时清除充值记录查询状态
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearRechargeRecordState()
        }
    }

    val timeRangeOptions = listOf(
        stringResource(R.string.recharge_time_1month),
        stringResource(R.string.recharge_time_3months),
        stringResource(R.string.recharge_time_1year),
        stringResource(R.string.recharge_time_4years),
    )

    Box(Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recharge_record_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearRechargeRecordState()
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
                    // 提示图标
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.recharge_hint),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                                text = { Text(stringResource(R.string.recharge_record_copy)) },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val text = getRechargeRecordTextContent(recordState, resources)
                                    copyToClipboard(context, text, resources.getString(R.string.recharge_record_export_title), snackbar)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recharge_record_export)) },
                                leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    pendingExportText = getRechargeRecordTextContent(recordState, resources)
                                    pendingExportLabel = resources.getString(R.string.recharge_record_export_title)
                                    saveFileLauncher.launch("electricity_recharge_record.txt")
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
            isRefreshing = recordState.isRefreshing,
            onRefresh = { viewModel.queryRechargeRecords(roomId) },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {

                // ========== 时间范围下拉菜单 ==========
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.recharge_record_time_range),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                    ) {
                        TextField(
                            value = timeRangeOptions[recordState.timeRange],
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !recordState.isQuerying)
                                .width(160.dp),
                            enabled = !recordState.isQuerying,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            )
                        )

                        AppScaledExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            timeRangeOptions.forEachIndexed { index, option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        viewModel.setRechargeRecordTimeRange(index)
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(8.dp))

                // ========== 查询结果区域 ==========
                when {
                    recordState.error != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = recordState.error ?: stringResource(R.string.common_unknown_error),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    recordState.list.isNotEmpty() -> {
                        val reversedList = recordState.list.reversed()
                        val total = reversedList.sumOf { it.buyTotal }
                        // 有数据，显示充值记录列表
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // 合计（移到最上方）
                            item {
                                Text(
            text = pluralStringResource(R.plurals.recharge_record_total, reversedList.size, String.format(Locale.US, "%.2f", total), reversedList.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }

                            // 充值记录列表（倒序排列，最新充值在上）
                            items(reversedList) { record ->
                                RechargeRecordCard(record)
                            }
                        }
                    }

                    else -> {
                        // 初始状态或已查询但无结果
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.recharge_record_no_data),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

}
    // ========== 提示弹窗 - Bottom Sheet ==========
    BottomSheetDialogV2(
        visible = showInfoDialog,
        onDismissRequest = { showInfoDialog = false },
        title = stringResource(R.string.recharge_hint)
    ) {
            Column {
                Text(stringResource(R.string.recharge_record_hint_item1))
                Spacer(modifier = Modifier.height(8.dp))
                val infoText = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(stringResource(R.string.recharge_record_hint_item2_prefix))
                    }
                    pushLink(
                        LinkAnnotation.Clickable(
                            tag = "url",
                            styles = TextLinkStyles(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        ) {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://electricitypay.cqwu.edu.cn/wxms/pages/buy/buy-list")
                            )
                            context.startActivity(intent)
                        }
                    )
                    append(stringResource(R.string.recharge_record_hint_item2_link))
                    pop()
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(stringResource(R.string.recharge_record_hint_item2_suffix))
                    }
                }
                Text(
                    text = infoText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
}

/**
 * 单条充值记录卡片
 */
@Composable
private fun RechargeRecordCard(record: BuyRecord) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 充值人（独立显示，每条记录可能不同）
        Text(
            text = stringResource(R.string.recharge_record_recharger, record.userName),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.recharge_record_time, record.buyTime),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.recharge_record_amount, String.format(Locale.US, "%.2f", record.buyTotal)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.recharge_record_order_no, record.orderNum),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 生成充值记录的纯文本内容（用于复制和导出）
 */
private fun getRechargeRecordTextContent(recordState: RechargeRecordState, resources: Resources): String {
    val sb = StringBuilder()
    sb.appendLine(resources.getString(R.string.recharge_record_export_title))
    sb.appendLine("=".repeat(40))

    if (recordState.list.isEmpty()) {
        sb.appendLine(resources.getString(R.string.recharge_record_no_data))
    } else {
        val reversedList = recordState.list.reversed()
        val total = reversedList.sumOf { it.buyTotal }
        sb.appendLine(resources.getQuantityString(R.plurals.recharge_record_total, reversedList.size, String.format(Locale.US, "%.2f", total), reversedList.size))
        sb.appendLine("-".repeat(40))
        reversedList.forEach { record ->
            sb.appendLine(resources.getString(R.string.recharge_record_recharger, record.userName))
            sb.appendLine(resources.getString(R.string.recharge_record_time, record.buyTime))
            sb.appendLine(resources.getString(R.string.recharge_record_amount, String.format(Locale.US, "%.2f", record.buyTotal)))
            sb.appendLine(resources.getString(R.string.recharge_record_order_no, record.orderNum))
            sb.appendLine("-".repeat(40))
        }
    }

    return sb.toString()
}
