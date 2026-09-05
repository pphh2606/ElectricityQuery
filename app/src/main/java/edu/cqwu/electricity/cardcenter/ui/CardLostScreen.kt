package edu.cqwu.electricity.cardcenter.ui

import edu.cqwu.electricity.theme.ui.currentTopBarColors

import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CreditCardOff
import edu.cqwu.electricity.common.ui.AppScaledAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import edu.cqwu.electricity.common.ui.BottomSheetDialogV2
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.cardcenter.data.CardLostInfo
import edu.cqwu.electricity.cardcenter.data.CardCenterApi
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.common.ui.LoadingDialog
import edu.cqwu.electricity.theme.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 卡挂失页面 — 本地化 UI
 *
 * 通过 HTTP 获取 EPay 卡挂失页面 HTML，解析卡号和卡状态后以原生卡片展示。
 * 用户点击"确认挂失"按钮后弹出二次确认对话框，确认后发起 POST 请求执行挂失。
 * 挂失成功后显示 Toast 提示并刷新卡状态。
 * 支持下拉刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardLostScreen(
    onBack: () -> Unit,
    onReLogin: () -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var cardInfo by remember { mutableStateOf<CardLostInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var requiresReLogin by remember { mutableStateOf(false) }

    // 确认对话框状态
    var showConfirmDialog by remember { mutableStateOf(false) }
    // 操作结果提示
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val snackbar = LocalSnackbarController.current
    var errorDialogMessage by remember { mutableStateOf<String?>(null) }

    val api = remember { CardCenterApi() }

    fun loadCardInfo(isRefresh: Boolean = false) {
        scope.launch {
            if (isRefresh) isRefreshing = true else isLoading = true
            errorMessage = null
            requiresReLogin = false

            val result = withContext(Dispatchers.IO) {
                api.fetchCardLostInfo()
            }

            result.onSuccess { info ->
                cardInfo = info
                isLoading = false
                isRefreshing = false
            }.onFailure { error ->
                isLoading = false
                isRefreshing = false
                if (error is SessionExpiredException) {
                    requiresReLogin = true
                    errorMessage = null
                } else {
                    errorMessage = error.message ?: resources.getString(R.string.card_lost_fetch_error)
                }
            }
        }
    }

    fun executeCardLost() {
        scope.launch {
            isSubmitting = true
            showConfirmDialog = false

            val result = withContext(Dispatchers.IO) {
                api.doCardLost()
            }

            isSubmitting = false

            result.onSuccess { response ->
                if (response.retcode == "0") {
                    // 挂失成功 — Toast 提示
                    snackbar.show(resources.getString(R.string.card_lost_success), ToastUtils.Type.SUCCESS)
                    // 延迟后刷新卡信息，更新卡状态为"挂失"
                    delay(800)
                    loadCardInfo()
                } else {
                    // 挂失失败 — 显示错误对话框
                    errorDialogMessage = response.retmsg.ifBlank { resources.getString(R.string.card_lost_failure) }
                }
            }.onFailure { error ->
                errorDialogMessage = error.message ?: resources.getString(R.string.card_lost_request_error)
            }
        }
    }

    // 首次加载
    LaunchedEffect(Unit) {
        loadCardInfo()
    }

    val topBarColors = currentTopBarColors()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.card_lost_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = topBarColors
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { loadCardInfo(isRefresh = true) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.card_fetching_info),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    errorMessage != null || requiresReLogin -> {
                        ReLoginContent(
                            errorMessage = errorMessage,
                            requiresReLogin = requiresReLogin,
                            onReLogin = onReLogin,
                            onRetry = { loadCardInfo() },
                        )
                    }

                    cardInfo != null -> {
                        CardLostContent(
                            cardInfo = cardInfo!!,
                            isSubmitting = isSubmitting,
                            onConfirmLost = { showConfirmDialog = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

        }
    }

    // 挂失提交中：全屏阻断加载（不可取消）
    if (isSubmitting) {
        LoadingDialog(message = stringResource(R.string.card_lost_processing))
    }

    // ── 二次确认对话框 ──
    if (showConfirmDialog) {
        AppScaledAlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.card_lost_confirm_title)) },
            text = { Text(stringResource(R.string.card_lost_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = { executeCardLost() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.card_lost_confirm_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // ── 错误提示 - Bottom Sheet ──
    BottomSheetDialogV2(
        visible = errorDialogMessage != null,
        onDismissRequest = { errorDialogMessage = null },
        title = stringResource(R.string.card_error_title)
    ) {
        Text(
            text = errorDialogMessage ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 卡挂失内容区 — 卡片信息展示 + 确认挂失按钮
 */
@Composable
private fun CardLostContent(
    cardInfo: CardLostInfo,
    isSubmitting: Boolean,
    onConfirmLost: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCardNormal = cardInfo.cardStatus.contains(stringResource(R.string.card_lost_normal_status), ignoreCase = true)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 卡信息卡片 ──
        item(key = "card_info") {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    CardInfoRow(
                        label = stringResource(R.string.card_lost_card_number),
                        value = cardInfo.cardNumber,
                        icon = Icons.Outlined.CreditCard
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    CardInfoRow(
                        label = stringResource(R.string.card_lost_card_status),
                        value = cardInfo.cardStatus,
                        icon = Icons.Outlined.CreditCardOff,
                        valueColor = if (isCardNormal)
                            Color(0xFF4CAF50)  // 正常 → 绿色
                        else
                            MaterialTheme.colorScheme.error  // 异常 → 红色
                    )
                }
            }
        }

        // ── 确认挂失按钮 ──
        item(key = "action_button") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onConfirmLost,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    enabled = !isSubmitting
                ) {
                    Text(
                        text = stringResource(R.string.card_lost_confirm_btn),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 提示文字
                Text(
                    text = stringResource(R.string.card_lost_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 卡信息行：图标 + 标签 + 值
 */
@Composable
private fun CardInfoRow(
    label: String,
    value: String,
    icon: ImageVector,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}
