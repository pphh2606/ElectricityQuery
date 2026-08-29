package edu.cqwu.electricity.accountmanagerv2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.ui.AppScaledAlertDialog
import edu.cqwu.electricity.common.ui.LabeledFieldRow
import edu.cqwu.electricity.common.ui.LoadingDialog
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import edu.cqwu.electricity.theme.util.ToastUtils

/**
 * 登录设备管理页 — 对应 CAS 网页 userOnline.do：
 * 按认证类型分组展示全部在线会话（IP / 登入时间 / 客户端类型），支持踢出非当前设备。
 *
 * 交互流程：
 * - 进入 / 下拉刷新 → 加载在线会话列表
 * - 点击其他设备的「踢出」→ 确认弹窗 → 调 removeOnlineUser.do → 成功提示并刷新列表
 * - 当前设备显示「当前设备」标签，不提供踢出按钮（与网页行为一致）
 * - 会话过期（响应为 CAS 登录页）→ 重新登录引导
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSessionScreen(
    onBack: () -> Unit,
    onReLogin: () -> Unit,
    viewModel: DeviceSessionViewModel = viewModel(),
) {
    val snackbar = LocalSnackbarController.current
    val state by viewModel.uiState.collectAsState()

    var pendingKick by remember { mutableStateOf<DeviceSession?>(null) }

    // 踢出结果消息（成功/失败），展示后消费
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.show(it, if (state.messageIsError) ToastUtils.Type.ERROR else ToastUtils.Type.SUCCESS)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.device_session_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = currentTopBarColors(),
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                state.requiresReLogin -> ReLoginContent(
                    requiresReLogin = true,
                    onReLogin = onReLogin,
                )
                state.loadError != null && state.sessions.isEmpty() -> ReLoginContent(
                    errorMessage = state.loadError,
                    requiresReLogin = false,
                    onReLogin = {},
                    onRetry = viewModel::refresh,
                )
                state.sessions.isEmpty() -> EmptySessions(
                    modifier = Modifier.fillMaxSize(),
                )
                else -> SessionList(
                    sessions = state.sessions,
                    onKickClick = { pendingKick = it },
                )
            }
        }
    }

    // 踢出确认弹窗
    pendingKick?.let { target ->
        AppScaledAlertDialog(
            onDismissRequest = { pendingKick = null },
            title = { Text(text = stringResource(R.string.device_session_kick_title)) },
            text = { Text(text = stringResource(R.string.device_session_kick_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingKick = null
                    viewModel.removeSession(target.id)
                }) {
                    Text(
                        text = stringResource(R.string.device_session_kick),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingKick = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }

    // 踢出进行中（阻断交互）
    if (state.kickingSessionId != null) {
        LoadingDialog(message = stringResource(R.string.device_session_kick_pending))
    }
}

/**
 * 会话列表：顶部提示小字 + 平铺会话卡片。
 * 提示说明客户端类型数据的来源与误差，随列表滚动。
 */
@Composable
private fun SessionList(
    sessions: List<DeviceSession>,
    onKickClick: (DeviceSession) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item(key = "tip") {
            Text(
                text = stringResource(R.string.device_session_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        items(sessions, key = { it.id }) { session ->
            SessionCard(
                session = session,
                onKickClick = { onKickClick(session) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * 单个会话卡片（折叠/展开结构）：
 * - 折叠态：通用设备图标 + 主行（当前设备"当前在线"/其他设备认证类型）+ 原始登入时间 + 展开箭头
 * - 展开态：完整显示 HTML 页面内容 — 用户IP（IPv6/IPv4）、认证类型、客户端类型、登入时间（可长按选取）、操作
 */
@Composable
private fun SessionCard(
    session: DeviceSession,
    onKickClick: () -> Unit,
) {
    var expanded by remember(session.id) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(if (expanded) 90f else 0f, label = "deviceCardArrow")
    // 折叠态主行：当前设备 → "当前在线"；其他设备 → 认证类型（免登录等，短文本不折叠）
    val headline = if (session.isCurrent) {
        stringResource(R.string.device_session_online)
    } else {
        session.authType.ifBlank { stringResource(R.string.device_session_unknown_device) }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── 折叠摘要头（点击切换展开）──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { expanded = !expanded })
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    // 登录设备可能是手机或电脑，统一使用通用设备图标
                    Icon(
                        imageVector = Icons.Outlined.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (session.loginTimeText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = session.loginTimeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(arrowRotation),
                )
            }

            // ── 展开区：HTML 完整内容（字段支持长按选取复制）──
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // 两个 IP（IPv6/IPv4）合并为同一字段，换行显示
                            val ipText = listOfNotNull(session.ipv6, session.ipv4).joinToString("\n")
                            if (ipText.isNotEmpty()) {
                                LabeledFieldRow(
                                    label = stringResource(R.string.device_session_ip_label),
                                    value = ipText,
                                )
                            }
                            LabeledFieldRow(
                                label = stringResource(R.string.device_session_auth_label),
                                value = session.authType,
                            )
                            LabeledFieldRow(
                                label = stringResource(R.string.device_session_client_label),
                                value = session.clientType,
                            )
                            if (session.loginTimeText.isNotEmpty()) {
                                LabeledFieldRow(
                                    label = stringResource(R.string.device_session_time_label),
                                    value = session.loginTimeText,
                                )
                            }
                        }
                    }

                    // 操作区：当前设备 → 标签；其他设备 → 踢出按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (session.isCurrent) {
                            CurrentDeviceBadge()
                        } else {
                            TextButton(onClick = onKickClick) {
                                Text(
                                    text = stringResource(R.string.device_session_kick),
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 当前设备小标签（primary 浅底圆角） */
@Composable
private fun CurrentDeviceBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Text(
            text = stringResource(R.string.device_session_current),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** 空状态：无其他在线设备 */
@Composable
private fun EmptySessions(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.device_session_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
