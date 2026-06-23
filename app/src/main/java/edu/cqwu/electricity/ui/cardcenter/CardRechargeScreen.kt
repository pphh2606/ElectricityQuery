package edu.cqwu.electricity.ui.cardcenter

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import edu.cqwu.electricity.util.ToastUtils

/**
 * 预设充值金额列表
 */
private val PRESET_AMOUNTS = listOf(20.0, 50.0, 100.0, 200.0, 500.0, 1000.0)

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

    // 订单创建成功后导航到支付页面（使用 ViewModel 中的状态防止预测性返回手势取消时重复导航）
    LaunchedEffect(uiState.orderResult, uiState.hasNavigatedToPayment) {
        if (uiState.orderResult != null && !uiState.hasNavigatedToPayment) {
            viewModel.markNavigatedToPayment()
            onNavigateToPayment()
        }
    }

    // 显示查询错误
    LaunchedEffect(uiState.queryError) {
        uiState.queryError?.let {
            snackbar.show(it, ToastUtils.Type.ERROR)
        }
    }

    // 显示创建订单错误
    LaunchedEffect(uiState.createOrderError) {
        uiState.createOrderError?.let {
            snackbar.show(it, ToastUtils.Type.ERROR)
        }
    }

    val hasQueriedSuccess = uiState.cardInfo != null

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
                    label = { Text("学号") },
                    placeholder = { Text("请输入学号") },
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
                            text = "查询",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 查询错误提示
            if (uiState.queryError != null) {
                Text(
                    text = uiState.queryError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
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
                    label = { Text("自定义金额") },
                    placeholder = { Text("输入其他金额") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 创建订单错误提示
                if (uiState.createOrderError != null) {
                    Text(
                        text = uiState.createOrderError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // 下一步按钮
                Button(
                    onClick = { viewModel.createOrder() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !uiState.isCreatingOrder && uiState.cardInfo?.isNormal == true,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isCreatingOrder) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (uiState.isCreatingOrder) "创建订单中..." else "下一步",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
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
            InfoRow("姓名", info.username)
            InfoRow("学号", info.idserial)
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

// ================================================================
//  预设金额网格
// ================================================================

/**
 * 预设金额网格
 */
@Composable
private fun AmountGrid(
    selectedAmount: Double?,
    onAmountSelected: (Double) -> Unit
) {
    // 每行3个按钮
    val rows = PRESET_AMOUNTS.chunked(3)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { amount ->
                    val isSelected = selectedAmount == amount
                    OutlinedButton(
                        onClick = { onAmountSelected(amount) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            text = "${amount.toInt()} 元",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
