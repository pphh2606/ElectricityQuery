package edu.cqwu.electricity.campusnetwork.speedtest.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.campusnetwork.speedtest.data.SpeedTestRecord
import edu.cqwu.electricity.campusnetwork.speedtest.engine.SpeedTestPhase
import edu.cqwu.electricity.campusnetwork.speedtest.engine.SpeedTestStats
import edu.cqwu.electricity.campusnetwork.speedtest.engine.SpeedTestTick
import edu.cqwu.electricity.common.ui.BottomSheetDialogV2
import edu.cqwu.electricity.common.ui.InfoLabelWidth
import edu.cqwu.electricity.common.ui.InfoRow
import edu.cqwu.electricity.theme.ui.LocalNavController
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import edu.cqwu.electricity.theme.ui.resolve

/** 网络服务：自助服务（SAM 自助服务系统）——与网络服务页同一站点；本页无会话，走裸地址需自行登录 */
private const val SELF_SERVICE_URL = "https://zzfw.cqwu.edu.cn/selfservice/"

/** 国际学术资源：发起申请 */
private const val ACADEMIC_APPLY_URL = "https://speedtest.cqwu.edu.cn/access/apply"

/** 国际学术资源：申请记录 */
private const val ACADEMIC_RECORDS_URL = "https://speedtest.cqwu.edu.cn/access/my"

/** 最近测速每页条数（一次拉 50 条、每页 10 条，左右箭头翻页） */
private const val RECENT_PAGE_SIZE = 10

/** 分组之间的统一间距（状态行/按钮/指标区三块为同一视觉组，组内间距见屏体） */
private val SectionSpacing = 20.dp

/**
 * 网速测试页 —— 原生复刻官方移动端测速 UI（白底扁平、胶囊按钮、2×2 指标四宫格）。
 * 顶部为项目标准 TopAppBar；页面主体布局对照官网截图/AI 描述。
 *
 * 状态→文案/按钮的映射由 [statusTextOf]/[buttonSpecOf] 唯一决定，无状态展示件在
 * `SpeedTestComponents.kt`，本文件只做数据绑定与骨架组装。
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
                // 底部留白 = 屏体 12dp + 尾间 24dp
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            // ── 状态行紧贴胶囊大按钮（无间距），其下 12dp 接指标区 ──
            Column {
                StatusLine(state = uiState, tick = tick, palette = palette)
                MainActionButton(
                    state = uiState,
                    tick = tick,
                    palette = palette,
                    onStart = { viewModel.startTest() },
                    onStop = { viewModel.stopTest() },
                    onRetry = { viewModel.retry() },
                )
                Spacer(modifier = Modifier.height(12.dp))
                MetricsSection(state = uiState, tick = tick, palette = palette)
            }

            // ── 提示横幅 ──
            InfoBanner(palette = palette)

            // ── 网络服务 ──
            ServiceSection(
                titleRes = R.string.speed_test_network_service,
                entries = listOf(
                    ServiceEntry(Icons.Outlined.Bolt, R.string.speed_test_service_plan, R.string.speed_test_service_plan_desc, localRoute = Routes.CAMPUS_NETWORK_PORTAL_SERVICE),
                    ServiceEntry(Icons.Outlined.Language, R.string.speed_test_service_self, R.string.speed_test_service_self_desc, SELF_SERVICE_URL),
                ),
            )

            // ── 国际学术资源 ──
            ServiceSection(
                titleRes = R.string.speed_test_academic_title,
                entries = listOf(
                    ServiceEntry(Icons.AutoMirrored.Outlined.Send, R.string.speed_test_academic_apply, R.string.speed_test_academic_apply_desc, ACADEMIC_APPLY_URL),
                    ServiceEntry(Icons.Outlined.History, R.string.speed_test_academic_records, R.string.speed_test_academic_records_desc, ACADEMIC_RECORDS_URL),
                ),
            )

            // ── 最近测速（仅获取到数据时渲染）──
            if (recent.isNotEmpty()) {
                RecentRecordsSection(records = recent, palette = palette)
            }
        }
    }
}

// ══════════════════════════════════════════════
//  状态 → 文案 / 按钮规格（纯函数，唯一映射来源）
// ══════════════════════════════════════════════

/** 状态行文案：资源 ID 与可选格式化参数；ERROR 分支的文案来自 UiMessage，由调用点解析 */
internal data class StatusText(@param:StringRes val res: Int, val arg: Any? = null)

internal fun statusTextOf(state: SpeedTestUiState, tick: SpeedTestTick): StatusText = when (state.status) {
    SpeedTestRunStatus.IDLE -> StatusText(R.string.speed_test_idle_hint)

    SpeedTestRunStatus.QUEUED -> state.position
        ?.let { StatusText(R.string.speed_test_queue_position, it) }
        ?: StatusText(R.string.speed_test_queued)

    SpeedTestRunStatus.RUNNING -> StatusText(
        when (tick.phase) {
            SpeedTestPhase.DOWNLOAD -> R.string.speed_test_dl_hint
            SpeedTestPhase.PING -> R.string.speed_test_ping_hint
            SpeedTestPhase.UPLOAD -> R.string.speed_test_ul_hint
        },
    )

    SpeedTestRunStatus.COMPLETED -> state.resultId
        ?.let { StatusText(R.string.speed_test_done_with_id, it) }
        ?: StatusText(R.string.speed_test_done)

    SpeedTestRunStatus.ERROR -> StatusText(R.string.campus_network_error_generic)
}

/** 胶囊按钮的展示规格：文案/配色/可用性/进度由状态唯一决定，动作由调用点另行映射 */
internal data class ButtonSpec(
    @param:StringRes val textRes: Int,
    val bg: Color,
    val fg: Color,
    val enabled: Boolean = true,
    val progress: Float = 0f,
    val overlayColor: Color = Color.Transparent,
)

internal fun buttonSpecOf(
    state: SpeedTestUiState,
    tick: SpeedTestTick,
    palette: SpeedTestPalette,
): ButtonSpec = when (state.status) {
    SpeedTestRunStatus.RUNNING -> ButtonSpec(
        textRes = R.string.speed_test_stop,
        bg = palette.stopButton,
        fg = palette.stopButtonText,
        progress = tick.progress,
        overlayColor = palette.progressOverlay,
    )

    SpeedTestRunStatus.QUEUED -> ButtonSpec(
        textRes = R.string.speed_test_queued,
        bg = palette.label.copy(alpha = 0.35f),
        fg = Color.White,
        enabled = false,
    )

    SpeedTestRunStatus.ERROR -> ButtonSpec(R.string.speed_test_retry_btn, palette.startButton, palette.startButtonText)

    SpeedTestRunStatus.IDLE -> ButtonSpec(R.string.speed_test_start, palette.startButton, palette.startButtonText)

    SpeedTestRunStatus.COMPLETED -> ButtonSpec(R.string.speed_test_retry, palette.startButton, palette.startButtonText)
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
    val statusText = statusTextOf(state, tick)
    val text = if (state.status == SpeedTestRunStatus.ERROR) {
        state.error?.resolve(resources) ?: stringResource(statusText.res)
    } else {
        statusText.arg?.let { stringResource(statusText.res, it) } ?: stringResource(statusText.res)
    }
    val textColor = if (state.status == SpeedTestRunStatus.ERROR) {
        palette.upload
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 测速中额外显示状态点（对齐官方进行态）
        if (state.status == SpeedTestRunStatus.RUNNING) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(palette.download),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontFamily = FontFamily.Serif,
        )
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
    val spec = buttonSpecOf(state, tick, palette)
    PillButton(
        text = stringResource(spec.textRes),
        bg = spec.bg,
        fg = spec.fg,
        enabled = spec.enabled,
        progress = spec.progress,
        overlayColor = spec.overlayColor,
        onClick = when (state.status) {
            SpeedTestRunStatus.RUNNING -> onStop
            SpeedTestRunStatus.ERROR -> onRetry
            SpeedTestRunStatus.QUEUED -> ({})
            SpeedTestRunStatus.IDLE, SpeedTestRunStatus.COMPLETED -> onStart
        },
    )
}

// ══════════════════════════════════════════════
//  指标区（活跃会话/排队等候 + 2×2 四宫格）
// ══════════════════════════════════════════════

/** 四宫格中的一个指标 */
private data class MetricSpec(
    @param:StringRes val labelRes: Int,
    val color: Color,
    val value: String,
    val letter: String? = null,
    val icon: ImageVector? = null,
    @param:StringRes val unitRes: Int,
)

@Composable
private fun MetricsSection(
    state: SpeedTestUiState,
    tick: SpeedTestTick,
    palette: SpeedTestPalette,
) {
    // 只有 RUNNING / COMPLETED 才展示引擎数值，其余显示 0（对齐官方就绪态）
    val show = state.status == SpeedTestRunStatus.RUNNING || state.status == SpeedTestRunStatus.COMPLETED
    fun mbps(v: Double) = if (show) SpeedTestStats.formatMbps(v) else "0"
    fun ms(v: Double) = if (show) SpeedTestStats.formatMs(v) else "0"

    val metrics = listOf(
        MetricSpec(R.string.speed_test_label_download, palette.download, mbps(tick.downloadMbps), "DL", Icons.Outlined.ArrowDownward, R.string.speed_test_unit_mbps),
        MetricSpec(R.string.speed_test_label_upload, palette.upload, mbps(tick.uploadMbps), "UL", Icons.Outlined.ArrowUpward, R.string.speed_test_unit_mbps),
        MetricSpec(R.string.speed_test_label_ping, palette.ping, ms(tick.pingMs), unitRes = R.string.speed_test_unit_ms),
        MetricSpec(R.string.speed_test_label_jitter, palette.jitter, ms(tick.jitterMs), unitRes = R.string.speed_test_unit_ms),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = {}),
    ) {
        // 1) 活跃会话 | 排队等候（IntrinsicSize.Max 支撑纵向分隔线高度）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
        ) {
            MetricCell(
                label = stringResource(R.string.speed_test_active_session),
                value = if (show && state.active > 0) state.active.toString() else "0",
                color = MaterialTheme.colorScheme.onSurface,
                valueSize = 18.sp,
                valueWeight = FontWeight.Bold,
                verticalPadding = 8.dp,
                labelSpacing = 2.dp,
                modifier = Modifier.weight(1f),
            )
            RowDivider(color = palette.divider)
            MetricCell(
                label = stringResource(R.string.speed_test_queue_wait),
                value = if (show) state.queue.toString() else "0",
                color = MaterialTheme.colorScheme.onSurface,
                valueSize = 18.sp,
                valueWeight = FontWeight.Bold,
                verticalPadding = 8.dp,
                labelSpacing = 2.dp,
                modifier = Modifier.weight(1f),
            )
        }

        // 2) 2×2 四宫格：下载/上传/延迟/抖动（每行 2 格，行间与格间分隔线随循环绘制）
        metrics.chunked(2).forEach { row ->
            CardDivider(palette = palette)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
            ) {
                row.forEachIndexed { index, spec ->
                    if (index > 0) RowDivider(color = palette.divider)
                    MetricCell(
                        label = stringResource(spec.labelRes),
                        value = spec.value,
                        color = spec.color,
                        letter = spec.letter,
                        icon = spec.icon,
                        unit = stringResource(spec.unitRes),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
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
            .clickable(onClick = {})
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
//  服务分组（网络服务 / 国际学术资源）
// ══════════════════════════════════════════════

/** 服务入口：图标 + 标题/副标题资源 + 点击目标（本地页面路由或内置浏览器 URL） */
private data class ServiceEntry(
    val icon: ImageVector,
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
    /** 内置浏览器目标 URL；为 null 时走 [localRoute] */
    val url: String? = null,
    /** 本地页面路由（如网络服务页）；非空时优先于 [url] */
    val localRoute: String? = null,
)

/**
 * 服务分组：卡片外标题 + 卡片内若干入口行（本地路由优先，否则经内置浏览器打开 url）。
 *
 * 用内层 Column 包住"标题 + 卡片"，避免外层 `spacedBy` 在两者之间再插一段间距。
 */
@Composable
private fun ServiceSection(
    @StringRes titleRes: Int,
    entries: List<ServiceEntry>,
) {
    val nav = LocalNavController.current
    Column {
        SectionTitle(text = stringResource(titleRes))
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard {
            entries.forEach { entry ->
                val title = stringResource(entry.titleRes)
                IconRow(
                    icon = entry.icon,
                    title = title,
                    subtitle = stringResource(entry.subtitleRes),
                    onClick = {
                        val target = entry.localRoute
                            ?: Routes.unifiedWebViewRoute(entry.url.orEmpty(), title)
                        nav.navigate(target)
                    },
                )
            }
        }
    }
}

// ══════════════════════════════════════════════
//  最近测速记录
// ══════════════════════════════════════════════

@Composable
private fun RecentRecordsSection(
    records: List<SpeedTestRecord>,
    palette: SpeedTestPalette,
) {
    val pages = remember(records) { records.chunked(RECENT_PAGE_SIZE) }
    var page by remember { mutableIntStateOf(1) }
    var selectedRecord by remember { mutableStateOf<SpeedTestRecord?>(null) }
    val current = page.coerceIn(1, pages.size)

    Column {
        SectionTitle(text = stringResource(R.string.speed_test_recent_title))
        Spacer(modifier = Modifier.height(8.dp))
        // 与原实现一致：卡片内容上下各留 4dp
        SettingsCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            pages[current - 1].forEachIndexed { index, record ->
                RecentRecordRow(
                    record = record,
                    palette = palette,
                    showBottomDivider = index != pages[current - 1].lastIndex,
                    onClick = { selectedRecord = record },
                )
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
                    text = "$current / ${pages.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Serif,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PageArrow(
                        icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        enabled = current > 1,
                        onClick = { page = current - 1 },
                    )
                    PageArrow(
                        icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        enabled = current < pages.size,
                        onClick = { page = current + 1 },
                    )
                }
            }
        }
    }

    // 点击记录行 → 底部弹窗展示该条完整字段
    RecordDetailSheet(record = selectedRecord, onDismiss = { selectedRecord = null })
}

/** 翻页箭头：禁用态用低透明度表示（与原实现一致） */
@Composable
private fun PageArrow(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(30.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.35f),
        )
    }
}

@Composable
private fun RecentRecordRow(
    record: SpeedTestRecord,
    palette: SpeedTestPalette,
    showBottomDivider: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                MetaText(text = formatTimestamp(record.timestamp))
                MetaText(text = record.ipAddress ?: "--")
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
        if (showBottomDivider) {
            CardDivider(palette = palette)
        }
    }
}

/**
 * 测速记录详情：底部弹窗展示该条记录的**全部字段**
 * （列表里只展示时间 / IP / DL / UL / ping）。
 *
 * 容器与信息行均复用通用组件；字段顺序固定，缺失值由 [InfoRow] 统一显示 "-"。
 */
@Composable
private fun RecordDetailSheet(record: SpeedTestRecord?, onDismiss: () -> Unit) {
    BottomSheetDialogV2(
        visible = record != null,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.speed_test_record_detail_title),
        icon = Icons.Outlined.Speed,
    ) {
        record?.let { r ->
            DetailRow(R.string.speed_test_record_time, formatTimestamp(r.timestamp))
            DetailRow(R.string.speed_test_label_download, mbpsOf(r.download))
            DetailRow(R.string.speed_test_label_upload, mbpsOf(r.upload))
            DetailRow(R.string.speed_test_label_ping, msOf(r.ping))
            DetailRow(R.string.speed_test_record_jitter, msOf(r.jitter))
            DetailRow(R.string.speed_test_record_ip, r.ipAddress.orEmpty())
            DetailRow(R.string.speed_test_record_isp, r.isp.orEmpty())
            DetailRow(R.string.speed_test_record_uuid, r.uuid.orEmpty())
            DetailRow(R.string.speed_test_record_language, r.language.orEmpty())
        }
    }
}

/** 详情字段行：固定标签宽度、允许换行与统一行距（复用通用 [InfoRow]，不重复布局逻辑） */
@Composable
private fun DetailRow(@StringRes labelRes: Int, value: String) {
    InfoRow(
        label = stringResource(labelRes),
        value = value,
        modifier = Modifier.padding(vertical = 8.dp),
        labelWidth = InfoLabelWidth,
        maxLines = Int.MAX_VALUE,
    )
}

/** 详情弹窗里的 Mbps 值（1 位小数 + 单位，与页面四宫格口径一致） */
@Composable
private fun mbpsOf(raw: String?): String =
    "${SpeedTestStats.formatMbps(value1(raw))} ${stringResource(R.string.speed_test_unit_mbps)}"

/** 详情弹窗里的 ms 值（取整 + 单位） */
@Composable
private fun msOf(raw: String?): String =
    "${SpeedTestStats.formatMs(value1(raw))} ${stringResource(R.string.speed_test_unit_ms)}"

/** 记录行的次级信息（时间 / IP）：小字灰 serif */
@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Serif,
    )
}

/** 字符串数值 → Double（非法/空回退 0.0） */
private fun value1(value: String?): Double = value?.toDoubleOrNull() ?: 0.0

/** ISO8601 时间戳 → 本地时区 `yyyy-MM-dd HH:mm:ss`；解析失败回退原始串（与前端 rte 行为一致） */
private val TimestampFormatter = java.time.format.DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(java.time.ZoneId.systemDefault())

private fun formatTimestamp(raw: String?): String {
    if (raw.isNullOrBlank()) return "--"
    return try {
        TimestampFormatter.format(java.time.OffsetDateTime.parse(raw))
    } catch (e: Exception) {
        raw
    }
}
