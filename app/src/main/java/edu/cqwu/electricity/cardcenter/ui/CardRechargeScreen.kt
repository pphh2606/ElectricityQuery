package edu.cqwu.electricity.cardcenter.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.login.data.AccountStore
import edu.cqwu.electricity.login.data.AccountManager
import edu.cqwu.electricity.payment.ui.AmountGrid
import edu.cqwu.electricity.theme.ui.BottomSheetDialog
import edu.cqwu.electricity.theme.ui.BottomSheetItem
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.theme.ui.LocalNavController
import edu.cqwu.electricity.theme.ui.LocalTopBarState
import edu.cqwu.electricity.theme.ui.toTopAppBarColors
import edu.cqwu.electricity.theme.util.ToastUtils

/**
 * 校园卡充值 — 学号输入 + 金额选择页面
 *
 * 流程：
 * 1. 用户输入学号，点击"查询"加载校园卡信息
 * 2. 查询成功后显示卡信息 + 充值金额选择
 * 3. 用户选择金额，点击"下一步"创建订单 → 导航到支付页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardRechargeScreen(
    viewModel: CardRechargeViewModel,
    onBack: () -> Unit,
    onNavigateToPayment: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = LocalSnackbarController.current
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
    val context = LocalContext.current
    val resources = LocalResources.current
    val nav = LocalNavController.current

    // ── 其他充值方式弹窗状态 ──
    var showOtherRechargeDialog by remember { mutableStateOf(false) }

    // ── 自动填充已登录用户的学号并查询 ──
    val loggedInStudentId = remember {
        AccountManager.getActiveUser()
            ?: AccountStore.getInstance(context).getAllAccountNames().firstOrNull()
    }
    LaunchedEffect(Unit) {
        viewModel.autoFillFromLogin(loggedInStudentId)
    }

    // 显示查询错误
    LaunchedEffect(uiState.queryError) {
        uiState.queryError?.let {
            snackbar.show(it, ToastUtils.Type.ERROR)
            viewModel.clearQueryError()
        }
    }

    val hasQueriedSuccess = uiState.cardInfo != null

    // 是否有有效金额且未超出校园卡最大余额限制
    val effectiveAmount = uiState.selectedAmount
        ?: uiState.customAmount.trim().toDoubleOrNull()
    val maxBalance = uiState.cardInfo?.maxBalanceYuan
    val hasValidAmount = effectiveAmount != null && effectiveAmount > 0
            && (maxBalance == null || effectiveAmount <= maxBalance)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.card_center_recharge),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refreshCardInfo() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ============================================================
                    //  学号输入区域
                    // ============================================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = uiState.studentId,
                            onValueChange = { viewModel.setStudentId(it) },
                            label = { Text(stringResource(R.string.card_recharge_student_id_label)) },
                            placeholder = { Text(stringResource(R.string.card_recharge_student_id_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isQuerying,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { viewModel.queryCardInfo() },
                            modifier = Modifier.height(56.dp),
                            enabled = uiState.studentId.trim().isNotBlank() && !uiState.isQuerying,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (uiState.isQuerying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.recharge_query),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // ============================================================
                    //  查询成功后显示卡信息 + 充值金额选择
                    // ============================================================
                    if (hasQueriedSuccess) {
                        // 校园卡信息卡片
                        CardInfoCard(uiState = uiState)

                        // 预设金额网格
                        AmountGrid(
                            selectedAmount = uiState.selectedAmount,
                            onAmountSelected = { viewModel.selectAmount(it) }
                        )

                        // 自定义金额输入
                        TextField(
                            value = uiState.customAmount,
                            onValueChange = { viewModel.setCustomAmount(it) },
                            label = { Text(stringResource(R.string.card_recharge_custom_amount_label)) },
                            placeholder = { Text(stringResource(R.string.card_recharge_custom_amount_placeholder)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 下一步按钮
                        Button(
                            onClick = { onNavigateToPayment() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = hasValidAmount && uiState.cardInfo?.isNormal == true,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.card_recharge_next_step),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }

                    // ============================================================
                    //  其他充值方式（始终可见，无论是否查询成功）
                    // ============================================================
                    Text(
                        text = stringResource(R.string.recharge_other_methods),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showOtherRechargeDialog = true }
                            .padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 充值记录 | 消费记录（固定在页面底部）──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { nav.navigate(Routes.FEE_SERVICE_HALL_ORDERS) }) {
                    Text(
                        stringResource(R.string.card_recharge_record),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(
                    text = "|",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )

                TextButton(onClick = { nav.navigate(Routes.BILL) }) {
                    Text(
                        stringResource(R.string.card_recharge_consume_record),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    // ================================================================
    //  其他充值方式 - Bottom Sheet（与电费充值 UI 一致）
    // ================================================================
    val cardRechargeH5Url = "https://pay.cqwu.edu.cn/mobile/#/eCardRechargePay?projectId=80bb5ee2189e4ca2bd5dff4513a0dae2"

    BottomSheetDialog(
        visible = showOtherRechargeDialog,
        onDismissRequest = { showOtherRechargeDialog = false },
        title = stringResource(R.string.recharge_other_method_title)
    ) {
        // 今日校园充值
        BottomSheetItem(
            icon = Icons.Outlined.Store,
            title = stringResource(R.string.recharge_method_campus),
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("campusnextins://"))
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    snackbar.show(resources.getString(R.string.recharge_install_campus_app))
                }
                showOtherRechargeDialog = false
            }
        )

        // 应用内 H5 充值
        BottomSheetItem(
            icon = Icons.Outlined.Public,
            title = stringResource(R.string.recharge_method_inapp_h5),
            onClick = {
                showOtherRechargeDialog = false
                nav.navigate(Routes.unifiedWebViewRoute(cardRechargeH5Url, resources.getString(R.string.card_center_recharge)))
            }
        )

        // 浏览器 H5 充值
        BottomSheetItem(
            icon = Icons.Outlined.OpenInBrowser,
            title = stringResource(R.string.recharge_method_browser_h5),
            onClick = {
                try {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(cardRechargeH5Url)
                    )
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    snackbar.show(resources.getString(R.string.common_no_browser))
                }
                showOtherRechargeDialog = false
            }
        )
    }
}

/**
 * 校园卡信息卡片
 */
@Composable
private fun CardInfoCard(uiState: CardRechargeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val info = uiState.cardInfo!!
            InfoRow(stringResource(R.string.card_recharge_info_name), info.username)
            InfoRow(stringResource(R.string.card_recharge_info_student_id), info.idserial)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

