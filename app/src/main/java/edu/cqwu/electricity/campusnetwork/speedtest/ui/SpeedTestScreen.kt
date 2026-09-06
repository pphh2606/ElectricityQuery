package edu.cqwu.electricity.campusnetwork.speedtest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.campusnetwork.speedtest.data.SpeedTestRecord
import edu.cqwu.electricity.campusnetwork.speedtest.engine.SpeedTestPhase
import edu.cqwu.electricity.campusnetwork.speedtest.engine.SpeedTestStats
import edu.cqwu.electricity.campusnetwork.speedtest.engine.SpeedTestTick
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import edu.cqwu.electricity.theme.ui.resolve
import kotlin.math.ceil

/**
 * 网速测试页 —— 原生复刻官方移动端测速 UI（白底扁平、胶囊按钮、2×2 指标四宫格）。
 * 顶部为项目标准 TopAppBar；页面主体布局对照官网截图/AI 描述。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(
    onBack: () -> Unit,
    viewModel: SpeedTestViewModel = viewModel(),
) {
    val uiState by viewModel.state.collectAsState()
    val tick by viewModel.tick.collectAsState()
    val recent by viewModel.recent.collectAsState()
    val resources = LocalResources.current
    val palette = speedTestPalette()
    val topBarColors = currentTopBarColors()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.speed_test_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // ── 状态点 + 状态文案 ──
            StatusLine(state = uiState, tick = tick, palette = palette)

            Spacer(modifier = Modifier.height(10.dp))

            // ── 胶囊大按钮 ──
            MainActionButton(
                state = uiState,
                tick = tick,
                palette = palette,
                onStart = { viewModel.startTest() },
                onStop = { viewModel.stopTest() },
                onRetry = { viewModel.retry() },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── 会话行 + 2×2 指标区 ──
            MetricsSection(state = uiState, tick = tick, palette = palette)

            Spacer(modifier = Modifier.height(20.dp))

            // ── 提示横幅 ──
            InfoBanner(palette = palette)

            // ── 最近测速（仅获取到数据时渲染）──
            if (recent.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                RecentRecordsSection(records = recent, palette = palette)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ══════════════════════════════════════════════
//  状态行
// ══════════════════════════════════════════════

@Composable
private fun StatusLine(
    state: SpeedTestUiState,
    tick: SpeedTestTick,
    palette: SpeedTestPalette,
) {
    val resources = LocalResources.current
    val isActive = state.status == SpeedTestRunStatus.RUNNING
    val dotColor = when (state.status) {
        SpeedTestRunStatus.RUNNING -> palette.download
        SpeedTestRunStatus.ERROR -> palette.upload
        else -> palette.label
    }
    val text = when (state.status) {
        SpeedTestRunStatus.IDLE -> resources.getString(R.string.speed_test_idle_hint)
        SpeedTestRunStatus.QUEUED ->
            state.position?.let { resources.getString(R.string.speed_test_queue_position, it) }
                ?: resources.getString(R.string.speed_test_queued)
        SpeedTestRunStatus.RUNNING -> when (tick.phase) {
            SpeedTestPhase.DOWNLOAD -> resources.getString(R.string.speed_test_dl_hint)
            SpeedTestPhase.PING -> resources.getString(R.string.speed_test_ping_hint)
            SpeedTestPhase.UPLOAD -> resources.getString(R.string.speed_test_ul_hint)
        }
        SpeedTestRunStatus.COMPLETED ->
            state.resultId?.let { resources.getString(R.string.speed_test_done_with_id, it) }
                ?: resources.getString(R.string.speed_test_done)
        SpeedTestRunStatus.ERROR ->
            state.error?.resolve(resources) ?: resources.getString(R.string.campus_network_error_generic)
    }
    val textColor = if (state.status == SpeedTestRunStatus.ERROR) {
        palette.upload
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isActive) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = text, style = MaterialTheme.typography.bodyMedium, color = textColor, fontFamily = FontFamily.Serif)
            }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Serif,
            )
        }
    }
}

// ══════════════════════════════════════════════
//  胶囊大按钮
// ══════════════════════════════════════════════

@Composable
private fun MainActionButton(
    state: SpeedTestUiState,
    tick: SpeedTestTick,
    palette: SpeedTestPalette,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state.status) {
        SpeedTestRunStatus.RUNNING -> CapsuleButton(
            textRes = R.string.speed_test_stop,
            bg = palette.stopButton,
            fg = palette.stopButtonText,
            overlayColor = palette.progressOverlay,
            progress = tick.progress,
            onClick = onStop,
        )

        SpeedTestRunStatus.QUEUED -> CapsuleButton(
            textRes = R.string.speed_test_queued,
            bg = palette.label.copy(alpha = 0.35f),
            fg = Color.White,
            enabled = false,
            onClick = {},
        )

        SpeedTestRunStatus.ERROR -> CapsuleButton(
            textRes = R.string.speed_test_retry_btn,
            bg = palette.startButton,
            fg = palette.startButtonText,
            onClick = onRetry,
        )

        // IDLE / COMPLETED：开始 / 重新测速
        else -> CapsuleButton(
            textRes = if (state.status == SpeedTestRunStatus.COMPLETED) {
                R.string.speed_test_retry
            } else {
                R.string.speed_test_start
            },
            bg = palette.startButton,
            fg = palette.startButtonText,
            onClick = onStart,
        )
    }
}

/** 全圆角胶囊按钮；progress>0 时在左侧叠加进度遮罩（测速中） */
@Composable
private fun CapsuleButton(
    textRes: Int,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
    overlayColor: Color = Color.Transparent,
    progress: Float = 0f,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(overlayColor),
            )
        }
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = fg,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = fg.copy(alpha = 0.9f),
            ),
            shape = RoundedCornerShape(23.dp),
        ) {
            Text(
                text = stringResource(textRes),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Serif,
            )
        }
    }
}

// ══════════════════════════════════════════════
//  指标区（活跃会话/排队等候 + 2×2 四宫格）
// ══════════════════════════════════════════════

@Composable
private fun MetricsSection(
    state: SpeedTestUiState,
    tick: SpeedTestTick,
    palette: SpeedTestPalette,
) {
    // 只有 RUNNING / COMPLETED 才展示引擎数值，其余显示 0（对齐官方就绪态）
    val showValues = state.status == SpeedTestRunStatus.RUNNING ||
        state.status == SpeedTestRunStatus.COMPLETED

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1) 活跃会话 | 排队等候（Row 用 IntrinsicSize.Max 支撑纵向分隔线高度）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
        ) {
            StatCell(
                label = stringResource(R.string.speed_test_active_session),
                value = if (showValues && state.active > 0) state.active.toString() else "0",
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(color = palette.divider)
            StatCell(
                label = stringResource(R.string.speed_test_queue_wait),
                value = if (showValues) state.queue.toString() else "0",
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = palette.divider)

        // 2) 2×2 四宫格：下载/上传/延迟/抖动
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
        ) {
            MetricCell(
                label = stringResource(R.string.speed_test_label_download),
                letter = "DL",
                valueText = if (showValues) SpeedTestStats.formatMbps(tick.downloadMbps) else "0",
                unit = stringResource(R.string.speed_test_unit_mbps),
                color = palette.download,
                icon = Icons.Outlined.ArrowDownward,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(color = palette.divider)
            MetricCell(
                label = stringResource(R.string.speed_test_label_upload),
                letter = "UL",
                valueText = if (showValues) SpeedTestStats.formatMbps(tick.uploadMbps) else "0",
                unit = stringResource(R.string.speed_test_unit_mbps),
                color = palette.upload,
                icon = Icons.Outlined.ArrowUpward,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = palette.divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
        ) {
            MetricCell(
                label = stringResource(R.string.speed_test_label_ping),
                letter = null,
                valueText = if (showValues) SpeedTestStats.formatMs(tick.pingMs) else "0",
                unit = stringResource(R.string.speed_test_unit_ms),
                color = palette.ping,
                icon = null,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(color = palette.divider)
            MetricCell(
                label = stringResource(R.string.speed_test_label_jitter),
                letter = null,
                valueText = if (showValues) SpeedTestStats.formatMs(tick.jitterMs) else "0",
                unit = stringResource(R.string.speed_test_unit_ms),
                color = palette.jitter,
                icon = null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 左标签右值类型的单元格（活跃会话/排队等候） */
@Composable
private fun StatCell(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Serif,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            fontFamily = FontFamily.Serif,
        )
    }
}

/** 2×2 网格中的速度指标格：小标签(带箭头) + 大数字 + 单位 */
@Composable
private fun MetricCell(
    label: String,
    letter: String?,
    valueText: String,
    unit: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 小标签行：图标 + 下载 DL / 上传 UL（Ping/Jitter 无字母后缀）
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = if (letter != null) "$label $letter" else label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Serif,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = valueText,
            fontSize = 40.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Serif,
            color = color,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = unit,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color.copy(alpha = 0.85f),
            fontFamily = FontFamily.Serif,
        )
    }
}

@Composable
private fun VerticalDivider(color: Color) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(color),
    )
}

// ══════════════════════════════════════════════
//  提示横幅
// ══════════════════════════════════════════════

@Composable
private fun InfoBanner(palette: SpeedTestPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.bannerBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = palette.bannerText,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.speed_test_banner),
            style = MaterialTheme.typography.bodySmall,
            color = palette.bannerText,
            fontFamily = FontFamily.Serif,
        )
    }
}

// ══════════════════════════════════════════════
//  最近测速记录
// ══════════════════════════════════════════════

/** 最近测速每页条数（用户指定：一次拉 50 条、每页 10 条，左右箭头翻页） */
private const val RECENT_PAGE_SIZE = 10

@Composable
private fun RecentRecordsSection(
    records: List<SpeedTestRecord>,
    palette: SpeedTestPalette,
) {
    val totalPages = maxOf(1, ceil(records.size / RECENT_PAGE_SIZE.toDouble()).toInt())
    var page by remember { mutableIntStateOf(1) }
    val safePage = page.coerceIn(1, totalPages)
    val start = (safePage - 1) * RECENT_PAGE_SIZE
    val pageRecords = records.drop(start).take(RECENT_PAGE_SIZE)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 4.dp),
    ) {
        // ── 标题 ──
        Text(
            text = stringResource(R.string.speed_test_recent_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = palette.label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
        )

        // ── 记录列表 ──
        pageRecords.forEachIndexed { index, record ->
            RecentRecordRow(record = record, palette = palette)
            if (index != pageRecords.lastIndex) {
                HorizontalDivider(color = palette.divider)
            }
        }

        // ── 分页脚条 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$safePage / $totalPages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Serif,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                PageArrowButton(
                    onClick = { page = (safePage - 1).coerceAtLeast(1) },
                    enabled = safePage > 1,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = if (safePage > 1) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                    )
                }
                PageArrowButton(
                    onClick = { page = (safePage + 1).coerceAtMost(totalPages) },
                    enabled = safePage < totalPages,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = if (safePage < totalPages) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRecordRow(
    record: SpeedTestRecord,
    palette: SpeedTestPalette,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 第一行：时间(左) + IP(右)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTimestamp(record.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Serif,
            )
            Text(
                text = record.ipAddress ?: "--",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Serif,
            )
        }

        // 第二行：DL | UL + ping ms
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeedValue(
                    label = "DL",
                    value = SpeedTestStats.formatMbps(value1(record.download)),
                    color = palette.download,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(14.dp)
                        .background(palette.divider),
                )
                Spacer(modifier = Modifier.width(10.dp))
                SpeedValue(
                    label = "UL",
                    value = SpeedTestStats.formatMbps(value1(record.upload)),
                    color = palette.upload,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = SpeedTestStats.formatMs(value1(record.ping)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Serif,
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = stringResource(R.string.speed_test_unit_ms),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Serif,
                )
            }
        }
    }
}

/** 小标签(如 DL/UL) + 色值 */
@Composable
private fun SpeedValue(
    label: String,
    value: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color.copy(alpha = 0.7f),
            fontFamily = FontFamily.Serif,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color,
            fontFamily = FontFamily.Serif,
        )
    }
}

@Composable
private fun PageArrowButton(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(30.dp),
    ) {
        content()
    }
}

/** 字符串数值 → Double（非法/空回退 0.0） */
private fun value1(value: String?): Double = value?.toDoubleOrNull() ?: 0.0

/** ISO8601 时间戳 → 本地时区 `yyyy-MM-dd HH:mm:ss`；解析失败回退原始串（与前端 rte 行为一致） */
private fun formatTimestamp(raw: String?): String {
    if (raw.isNullOrBlank()) return "--"
    return try {
        val ldt = java.time.OffsetDateTime.parse(raw)
            .atZoneSameInstant(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        String.format(
            java.util.Locale.US,
            "%04d-%02d-%02d %02d:%02d:%02d",
            ldt.year, ldt.monthValue, ldt.dayOfMonth,
            ldt.hour, ldt.minute, ldt.second,
        )
    } catch (e: Exception) {
        raw
    }
}
