package edu.cqwu.electricity.ui.recharge

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.data.model.BuyRecord
import edu.cqwu.electricity.ui.components.BottomSheetDialog
import edu.cqwu.electricity.ui.electricity.ElectricityUiState
import edu.cqwu.electricity.ui.electricity.ElectricityViewModel
import edu.cqwu.electricity.util.ToastUtils

// 三点菜单
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu

// 剪贴板与文件导出
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.components.SnackbarController

// 提示弹窗
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Info

/**
 * 充值记录查询页面
 *
 * UI 布局：
 * - 标题栏：← 返回按钮 + "查询充值记录" + ⋮ 三点菜单（复制/导出）
 * - 查询时间范围下拉菜单（一个月 / 三个月 / 一年 / 四年）
 * - 充值记录结果列表（支持下拉刷新）
 * - 进入页面自动以 userId=0 查询当前房间全部充值记录
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RechargeRecordScreen(
    viewModel: ElectricityViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)

    // 下拉菜单状态
    var dropdownExpanded by remember { mutableStateOf(false) }

    // 三点菜单状态
    var showMenu by remember { mutableStateOf(false) }

    // 提示弹窗状态
    var showInfoDialog by remember { mutableStateOf(false) }

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

    // 进入页面自动查询充值记录（仅在首次进入时触发）
    val isFirstLoad = remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (isFirstLoad.value) {
            isFirstLoad.value = false
            viewModel.queryRechargeRecords()
        }
    }

    // 页面离开时清除充值记录查询状态（不拦截返回手势，让预测性返回动画正常工作）
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearRechargeRecordState()
        }
    }

    // 检测房间是否已切换：如果当前房间与上次查询的房间不同，自动重新查询
    val currentRoomId = uiState.selectedRoom?.id
    LaunchedEffect(currentRoomId) {
        if (currentRoomId != null
            && uiState.rechargeRecordHasQueried
            && uiState.rechargeRecordRoomId.isNotEmpty()
            && currentRoomId != uiState.rechargeRecordRoomId
        ) {
            viewModel.clearRechargeRecordState()
            viewModel.queryRechargeRecords()
        }
    }

    val timeRangeOptions = listOf("一个月", "三个月", "一年", "四年")

    Box(Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("查询充值记录", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearRechargeRecordState()
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
                    // 提示图标
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "提示",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                                    val text = getRechargeRecordTextContent(uiState)
                                    copyToClipboard(context, text, "查询充值记录", snackbar)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导出") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    pendingExportText = getRechargeRecordTextContent(uiState)
                                    pendingExportLabel = "查询充值记录"
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
            isRefreshing = uiState.rechargeRecordIsRefreshing,
            onRefresh = { viewModel.queryRechargeRecords() },
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
                        text = "查询时间范围：",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                    ) {
                        TextField(
                            value = timeRangeOptions[uiState.rechargeRecordTimeRange],
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor() // menuAnchor() is deprecated but still works
                                .width(160.dp),
                            enabled = !uiState.rechargeRecordIsQuerying,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            )
                        )

                        ExposedDropdownMenu(
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
                    uiState.rechargeRecordError != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.rechargeRecordError ?: "未知错误",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    uiState.rechargeRecordList.isNotEmpty() -> {
                        val reversedList = uiState.rechargeRecordList.reversed()
                        val total = reversedList.sumOf { it.buyTotal }
                        // 有数据，显示充值记录列表
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // 合计（移到最上方）
                            item {
                                Text(
                                    text = "合计充值：${String.format("%.2f", total)} 元（共 ${reversedList.size} 笔）",
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
                                text = "未查询到记录",
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
    if (showInfoDialog) {
        BottomSheetDialog(
            onDismissRequest = { showInfoDialog = false },
            title = "提示"
        ) {
            Column {
                Text("1. 查询内容仅供参考，请以实际充值数量为准。")
                Spacer(modifier = Modifier.height(8.dp))
                val infoText = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append("2. 如果认为充值记录不准，可以访问")
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
                    append("此处")
                    pop()
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append("查看官方充值记录。")
                    }
                }
                Text(
                    text = infoText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
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
            text = "充值人：${record.userName}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "充值时间：${record.buyTime}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "充值金额：${String.format("%.2f", record.buyTotal)} 元",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "订单号：${record.orderNum}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 生成充值记录的纯文本内容（用于复制和导出）
 */
private fun getRechargeRecordTextContent(uiState: ElectricityUiState): String {
    val sb = StringBuilder()
    sb.appendLine("查询充值记录")
    sb.appendLine("=".repeat(40))

    if (uiState.rechargeRecordList.isEmpty()) {
        sb.appendLine("未查询到充值记录")
    } else {
        val reversedList = uiState.rechargeRecordList.reversed()
        val total = reversedList.sumOf { it.buyTotal }
        // 合计放到最前面
        sb.appendLine("合计充值：${String.format("%.2f", total)} 元（共 ${reversedList.size} 笔）")
        sb.appendLine("-".repeat(40))
        // 记录倒序输出
        reversedList.forEach { record ->
            sb.appendLine("充值人：${record.userName}")
            sb.appendLine("充值时间：${record.buyTime}")
            sb.appendLine("充值金额：${String.format("%.2f", record.buyTotal)} 元")
            sb.appendLine("订单号：${record.orderNum}")
            sb.appendLine("-".repeat(40))
        }
    }

    return sb.toString()
}

/**
 * 将文本复制到系统剪贴板并显示提示
 */
private fun copyToClipboard(context: Context, text: String, label: String, snackbar: SnackbarController) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    snackbar.show("已复制到剪贴板", ToastUtils.Type.SUCCESS)
}
