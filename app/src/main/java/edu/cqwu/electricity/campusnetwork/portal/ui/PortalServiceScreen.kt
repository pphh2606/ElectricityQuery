package edu.cqwu.electricity.campusnetwork.portal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.campusnetwork.portal.data.PortalClient
import edu.cqwu.electricity.campusnetwork.portal.data.PortalOnlineInfo
import edu.cqwu.electricity.common.ui.BottomSheetDialogV2
import edu.cqwu.electricity.common.ui.BottomSheetItem
import edu.cqwu.electricity.common.ui.InfoLabelWidth
import edu.cqwu.electricity.common.ui.InfoRow
import edu.cqwu.electricity.common.ui.InfoRowDivider
import edu.cqwu.electricity.common.ui.InfoSectionTitle
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.theme.ui.LocalNavController
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import edu.cqwu.electricity.theme.ui.resolve
import edu.cqwu.electricity.theme.util.ToastUtils

/** 网关流量上限超过该量级（MB）视为不限：实测值 ≈3.25 PB，阈值属推断 */
private const val UNLIMITED_TRAFFIC_MB = 1024.0 * 1024 * 1024

/** 剩余时长低于该秒数时转为警示色 */
private const val REMAINING_WARN_SECONDS = 300L

/**
 * SAM 自助服务系统入口（与网速测试页同一地址）。
 *
 * 不再使用网关下发的 `selfUrl`：实测该链接只是个把用户名预填、仍需手输密码的登录页
 * （`login_judge.jsf` 会丢弃凭据并跳到 `login_self.jsf`），并非免密入口。
 */
private const val SELF_SERVICE_URL = "https://zzfw.cqwu.edu.cn/selfservice/"

/** 平铺信息行的统一内边距：水平 16dp 与分组标题、分隔线缩进对齐 */
private val RowPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

/**
 * 网络服务页 —— 本地化认证网关（eportal）的会话展示与操作。
 *
 * 数据**只来自认证网关** `222.179.99.144`，与「接入者信息」页的 SAM 档案（经测速站代理）
 * 是两个不同服务器的来源，因此页面顶部固定标注来源，不与另一页面合并展示。
 *
 * 版式与「接入者信息」一致：**白底平铺**，分组标题 + 键值行 + 行间细分隔线，
 * 字段行统一使用 `common/ui` 的 [InfoRow] 三件套（同一套标签宽度与换行策略）。
 *
 * 本地化范围：会话状态、切换服务、断开网络、本机无感认证开关、自助服务免密入口；
 * 账号密码认证不在此页（保持跳官方认证页）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalServiceScreen(
    onBack: () -> Unit,
    viewModel: PortalServiceViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val resources = LocalResources.current
    val nav = LocalNavController.current
    val snackbar = LocalSnackbarController.current
    val topBarColors = currentTopBarColors()

    var showServiceSheet by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val errorText = state.error?.resolve(resources)
    val hasContent = state.info != null

    // 两处跳转各只写一次，避免在多个分支里重复拼路由
    val openAuthPage = {
        nav.navigate(
            Routes.unifiedWebViewRoute(
                PortalClient.BASE_URL,
                resources.getString(R.string.portal_link_auth_page),
            ),
        )
    }
    val openSelfService = {
        nav.navigate(
            Routes.unifiedWebViewRoute(
                SELF_SERVICE_URL,
                resources.getString(R.string.portal_link_self_service),
            ),
        )
    }

    // 一次性成功提示
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.show(it.resolve(resources), ToastUtils.Type.SUCCESS)
            viewModel.consumeNotice()
        }
    }

    // 已有内容时的操作失败用 Snackbar；整页错误（无内容）由错误态承载
    LaunchedEffect(errorText) {
        if (errorText != null && hasContent) {
            snackbar.show(errorText, ToastUtils.Type.ERROR)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.portal_service_title),
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
        // 与其他页面一致：下拉刷新（不再在标题栏放刷新按钮）
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.load() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // 说明：可滚动内容各自包一层 verticalScroll——错误态用的 ReLoginContent
            // 依赖 fillMaxSize 做整页居中，放进可滚动列会导致高度约束无限、居中失效。
            val info = state.info
            when {
                // 有数据优先：下拉刷新期间保留已有内容
                info != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    OnlineContent(
                        state = state,
                        info = info,
                        onSwitchClick = { showServiceSheet = true },
                        onMabToggle = { viewModel.setMab(it) },
                        onLogoutClick = { showLogoutDialog = true },
                        onOpenSelfService = openSelfService,
                        onOpenAuthPage = openAuthPage,
                    )
                }

                // 网关明确未报告会话：中性态（不等于不能上网）
                state.noSession -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    NoSessionContent(onOpenAuthPage = openAuthPage)
                }

                // 加载/刷新中：内容区留空，由 PullToRefreshBox 的顶部指示器呈现
                state.isRefreshing -> Unit

                // 其余情况统一收敛为一种错误态（细节进 AppLog），样式对齐其它页面的通用错误态
                else -> ReLoginContent(
                    errorMessage = errorText ?: stringResource(R.string.portal_error_unreachable),
                    requiresReLogin = false,
                    onReLogin = {},
                    onRetry = { viewModel.load() },
                )
            }
        }
    }

    ServiceSheet(
        visible = showServiceSheet,
        services = state.services,
        current = state.info?.service,
        onPick = { service ->
            showServiceSheet = false
            viewModel.switchService(service)
        },
        onDismiss = { showServiceSheet = false },
    )

    // 断开确认：与「打开外部应用」弹窗同构（拖动手柄 + 图标标题 + 居中说明 + 上下全宽按钮）
    BottomSheetDialogV2(
        visible = showLogoutDialog,
        onDismissRequest = { showLogoutDialog = false },
        title = stringResource(R.string.portal_logout_confirm_title),
        icon = Icons.Outlined.LinkOff,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.portal_logout_confirm_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { showLogoutDialog = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Button(
                onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = stringResource(R.string.common_confirm),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ══════════════════════════════════════════════
//  在线内容（平铺）
// ══════════════════════════════════════════════

@Composable
private fun OnlineContent(
    state: PortalServiceUiState,
    info: PortalOnlineInfo,
    onSwitchClick: () -> Unit,
    onMabToggle: (Boolean) -> Unit,
    onLogoutClick: () -> Unit,
    onOpenSelfService: () -> Unit,
    onOpenAuthPage: () -> Unit,
) {
    // ── 会话状态 ──
    InfoSectionTitle(text = stringResource(R.string.portal_group_session))
    OnlineStatusRow(info = info)
    RemainingBlock(seconds = state.remainingSeconds)

    InfoRowDivider()
    InfoRow(
        label = stringResource(R.string.portal_field_traffic),
        value = trafficText(info.maxFlow).orEmpty(),
        modifier = Modifier.padding(RowPadding),
        labelWidth = InfoLabelWidth,
        maxLines = Int.MAX_VALUE,
    )
    InfoRowDivider()
    InfoRow(
        label = stringResource(R.string.portal_field_ip),
        value = info.userIp.orEmpty(),
        modifier = Modifier.padding(RowPadding),
        labelWidth = InfoLabelWidth,
        maxLines = Int.MAX_VALUE,
    )
    InfoRowDivider()
    InfoRow(
        label = stringResource(R.string.portal_field_mac),
        value = formatMac(info.userMac).orEmpty(),
        modifier = Modifier.padding(RowPadding),
        labelWidth = InfoLabelWidth,
        maxLines = Int.MAX_VALUE,
    )
    InfoRowDivider()
    InfoRow(
        label = stringResource(R.string.portal_field_gateway),
        value = info.webGateIp.orEmpty(),
        modifier = Modifier.padding(RowPadding),
        labelWidth = InfoLabelWidth,
        maxLines = Int.MAX_VALUE,
    )
    InfoRowDivider()
    InfoRow(
        label = stringResource(R.string.portal_field_login_type),
        value = loginTypeText(info.loginType).orEmpty(),
        modifier = Modifier.padding(RowPadding),
        labelWidth = InfoLabelWidth,
        maxLines = Int.MAX_VALUE,
    )
    InfoRowDivider()
    InfoRow(
        label = stringResource(R.string.portal_field_package),
        value = info.userPackage.orEmpty(),
        modifier = Modifier.padding(RowPadding),
        labelWidth = InfoLabelWidth,
        maxLines = Int.MAX_VALUE,
    )
    InfoRowDivider()
    InfoRow(
        label = stringResource(R.string.portal_field_group),
        value = info.userGroup.orEmpty(),
        modifier = Modifier.padding(RowPadding),
        labelWidth = InfoLabelWidth,
        maxLines = Int.MAX_VALUE,
    )

    // 网关通知放在会话状态之后、服务之前
    NoticeNote(notices = state.notices)

    // ── 服务 ──
    InfoSectionTitle(text = stringResource(R.string.portal_group_service))
    SettingsCard {
        ServiceSwitchRow(state = state, onClick = onSwitchClick)
    }

    // ── 无感认证 ──
    InfoSectionTitle(text = stringResource(R.string.portal_group_mab))
    SettingsCard {
        MabRow(state = state, onToggle = onMabToggle)
    }

    // ── 操作 ──
    InfoSectionTitle(text = stringResource(R.string.portal_group_actions))
    SettingsCard {
        Button(
            onClick = onLogoutClick,
            enabled = !state.isApplying,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Text(text = stringResource(R.string.portal_action_logout))
        }
    }

    // ── 相关入口 ──
    InfoSectionTitle(text = stringResource(R.string.portal_group_links))
    SettingsCard {
        LinkRow(
            icon = Icons.Outlined.Language,
            title = stringResource(R.string.portal_link_self_service),
            onClick = onOpenSelfService,
        )
        LinkRow(
            icon = Icons.AutoMirrored.Outlined.OpenInNew,
            title = stringResource(R.string.portal_link_auth_page),
            onClick = onOpenAuthPage,
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
}

/** 在线状态行：状态点 + 文案 + 右侧服务名 */
@Composable
private fun OnlineStatusRow(info: PortalOnlineInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.portal_status_online),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = info.service.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 剩余可用时长：本地倒计时，居中大字 */
@Composable
private fun RemainingBlock(seconds: Long?) {
    val lowRemaining = seconds != null && seconds <= REMAINING_WARN_SECONDS
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = remainingText(seconds),
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            color = if (lowRemaining) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.portal_remaining_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 当前服务：可点击切换；候选列表取不到时不可点击（箭头淡化） */
@Composable
private fun ServiceSwitchRow(state: PortalServiceUiState, onClick: () -> Unit) {
    val switchable = state.services.isNotEmpty() && !state.isApplying
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (switchable) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.portal_service_current),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = state.info?.service.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (switchable) 0.5f else 0.2f,
            ),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** 本机无感认证开关 */
@Composable
private fun MabRow(state: PortalServiceUiState, onToggle: (Boolean) -> Unit) {
    val allowed = state.info?.isAlowMab != false
    // 设备列表只在 success 态可得（wait 态同名字段是服务串），拿不到时回退到静态说明
    val hint = when {
        !allowed -> stringResource(R.string.portal_mab_not_allowed)
        state.mabDevices.isEmpty() -> stringResource(R.string.portal_mab_desc)
        else -> stringResource(
            R.string.portal_mab_bound,
            state.mabDevices.size,
            state.info?.mabInfoMaxCount ?: state.mabDevices.size,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 标题 + 副标题列为一行（同设置页 SettingRow），右侧接开关
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.portal_mab_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = state.info?.hasMabInfo == true,
            onCheckedChange = onToggle,
            enabled = allowed && !state.isApplying,
        )
    }
}

/** 设置页风格的卡片容器：16dp 圆角 + surfaceContainerLow 底色（对齐 PersonalizationScreen） */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(content = content)
    }
}

/** 可点击入口行：图标 + 文案 + 右箭头（尺寸与设置页 SettingRow 一致） */
@Composable
private fun LinkRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp),
        )
    }
}

// ══════════════════════════════════════════════
//  网关无会话 / 错误
// ══════════════════════════════════════════════

/**
 * 网关未报告会话时的中性空状态。
 *
 * 实测"网关查不到会话" ≠ "不能上网"：设备通过无感认证自动上线时会命中这种状态，
 * 此时公网仍可访问。因此这里只陈述事实并给出认证入口，不断言未认证。
 */
@Composable
private fun NoSessionContent(onOpenAuthPage: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
        Text(
            text = stringResource(R.string.portal_no_session_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.portal_no_session_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onOpenAuthPage,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.portal_open_auth_page))
        }
    }
}

// ══════════════════════════════════════════════
//  提示条（来源 / 网关通知）
// ══════════════════════════════════════════════

/**
 * 网关通知（如「弱密码会导致下线」）：内容是服务端中文原文，直接展示、不走资源。
 * 网关没有通知时整块不渲染。
 */
@Composable
private fun NoticeNote(notices: List<String>) {
    if (notices.isEmpty()) return

    NoteRow(
        icon = Icons.Outlined.WarningAmber,
        iconTint = MaterialTheme.colorScheme.error,
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
    ) {
        notices.forEachIndexed { index, text ->
            if (index > 0) Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 提示条外壳：圆角底 + 图标 + 内容列（来源条与通知条共用） */
@Composable
private fun NoteRow(
    icon: ImageVector,
    iconTint: Color,
    containerColor: Color,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column { content() }
    }
}

// ══════════════════════════════════════════════
//  服务选择
// ══════════════════════════════════════════════

/** 服务选择：项目通用底部弹窗 + 单选列表项（同「夜间模式」弹窗） */
@Composable
private fun ServiceSheet(
    visible: Boolean,
    services: List<String>,
    current: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheetDialogV2(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.portal_service_pick),
    ) {
        services.forEach { service ->
            BottomSheetItem(
                icon = Icons.Outlined.Wifi,
                title = service,
                selected = service == current,
                onClick = { onPick(service) },
            )
        }
    }
}

// ══════════════════════════════════════════════
//  展示格式化（纯展示，不参与业务判断）
// ══════════════════════════════════════════════

/** 剩余时长：天/小时/分钟三档；未知返回占位 */
@Composable
private fun remainingText(seconds: Long?): String {
    if (seconds == null) return stringResource(R.string.portal_remaining_unknown)
    return when {
        seconds >= 86_400 -> stringResource(
            R.string.portal_remaining_days,
            seconds / 86_400,
            seconds % 86_400 / 3_600,
        )

        seconds >= 3_600 -> stringResource(
            R.string.portal_remaining_hours,
            seconds / 3_600,
            seconds % 3_600 / 60,
        )

        else -> stringResource(R.string.portal_remaining_minutes, seconds / 60)
    }
}

/** 流量上限：超过 [UNLIMITED_TRAFFIC_MB] 视为不限；无法解析返回 null 由调用方兜底 */
@Composable
private fun trafficText(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val matched = Regex("([\\d.]+)\\s*([KMGTP]?B)", RegexOption.IGNORE_CASE).find(raw) ?: return null
    val value = matched.groupValues[1].toDoubleOrNull() ?: return null
    val mb = when (matched.groupValues[2].uppercase()) {
        "KB" -> value / 1024
        "GB" -> value * 1024
        "TB" -> value * 1024 * 1024
        "PB" -> value * 1024 * 1024 * 1024
        else -> value
    }
    if (mb >= UNLIMITED_TRAFFIC_MB) return stringResource(R.string.portal_traffic_unlimited)
    val (scaled, unit) = when {
        mb >= 1024 * 1024 -> mb / 1024 / 1024 to "TB"
        mb >= 1024 -> mb / 1024 to "GB"
        else -> mb to "MB"
    }
    return "${format1(scaled)} $unit"
}

/** 12 位无分隔 MAC → `AA:BB:CC:DD:EE:FF`；长度不符原样返回 */
private fun formatMac(raw: String?): String? {
    val mac = raw?.trim()?.uppercase() ?: return null
    if (mac.length != 12 || !mac.all { it.isDigit() || it in 'A'..'F' }) return mac
    return mac.chunked(2).joinToString(":")
}

/** 登录方式：实测仅见 "3"（网页认证），未见其它取值时不再猜 */
@Composable
private fun loginTypeText(raw: String?): String? = when (raw) {
    "3" -> stringResource(R.string.portal_login_type_portal)
    else -> raw
}

/** 保留 1 位小数（与测速页的数值展示口径一致） */
private fun format1(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)
