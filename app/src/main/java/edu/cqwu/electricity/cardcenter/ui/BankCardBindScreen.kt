@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package edu.cqwu.electricity.cardcenter.ui

import androidx.compose.material3.IconButtonDefaults
import edu.cqwu.electricity.common.ui.AppSaveFab
import edu.cqwu.electricity.common.ui.BlurPullRefreshIndicator
import edu.cqwu.electricity.common.ui.BlurScaffold
import edu.cqwu.electricity.common.ui.transparentTopBarColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import edu.cqwu.electricity.R
import edu.cqwu.electricity.cardcenter.data.BankOption
import edu.cqwu.electricity.common.ui.ConfirmBottomSheet
import edu.cqwu.electricity.common.ui.LoadingDialog
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.common.util.ToastUtils

/** 学生绑定银行卡原生页面。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankCardBindScreen(
    viewModel: BankCardBindViewModel,
    onBack: () -> Unit,
    onReLogin: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val resources = LocalResources.current
    val snackbar = LocalSnackbarController.current
    val topBarColors = transparentTopBarColors()
    var showUnbindConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.loadError) {
        uiState.loadError?.let {
            snackbar.show(it, ToastUtils.Type.ERROR)
            viewModel.clearLoadError()
        }
    }
    LaunchedEffect(uiState.submitError) {
        uiState.submitError?.let {
            snackbar.show(it, ToastUtils.Type.ERROR)
            viewModel.clearSubmitError()
        }
    }
    LaunchedEffect(uiState.result) {
        uiState.result?.let { result ->
            snackbar.show(
                result.desc.ifBlank { result.title },
                if (result.isSuccess) ToastUtils.Type.SUCCESS else ToastUtils.Type.ERROR
            )
            viewModel.clearResult()
        }
    }

    BlurScaffold(
        // 键盘弹出时让出 IME 高度，避免遮挡右下角"绑定"按钮
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.bank_card_bind_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack) {
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
    ) { topInset ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullState,
            indicator = { BlurPullRefreshIndicator(pullState, uiState.isRefreshing, topInset) },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                uiState.requiresReLogin -> {
                    ReLoginContent(
                        errorMessage = null,
                        requiresReLogin = true,
                        onReLogin = onReLogin,
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.padding(top = topInset),
                    )
                }

                uiState.banks.isEmpty() && uiState.loadError != null -> {
                    ReLoginContent(
                        errorMessage = uiState.loadError
                            ?: resources.getString(R.string.bank_card_fetch_failed),
                        requiresReLogin = false,
                        onReLogin = {},
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.padding(top = topInset),
                    )
                }

                uiState.banks.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    )
                }

                else -> {
                    BankCardBindContent(
                        uiState = uiState,
                        onSelectBank = viewModel::selectBank,
                        onCardNoChange = viewModel::setCardNo,
                        onBind = viewModel::bind,
                        onUnbindClick = { showUnbindConfirm = true },
                        topInset = topInset,
                    )
                }
            }
        }
    }

    // 解绑确认弹窗
    ConfirmBottomSheet(
        visible = showUnbindConfirm,
        onDismissRequest = { showUnbindConfirm = false },
        title = stringResource(R.string.bank_card_unbind_confirm_title),
        message = stringResource(R.string.bank_card_unbind_confirm_text),
        confirmText = stringResource(R.string.bank_card_unbind_confirm_btn),
        onConfirm = {
            showUnbindConfirm = false
            viewModel.unbind()
        },
    )

    if (uiState.isSubmitting) {
        LoadingDialog(message = stringResource(R.string.bank_card_binding))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BankCardBindContent(
    uiState: BankCardBindUiState,
    onSelectBank: (String) -> Unit,
    onCardNoChange: (String) -> Unit,
    onBind: () -> Unit,
    onUnbindClick: () -> Unit,
    topInset: Dp = 0.dp,
) {
    val status = uiState.status
    val showUnbind = status?.isBound == true

    // 保存按钮改为右下角浮动：不再参与 Column 布局，背后不会留出一条遮挡横带
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 底部预留浮动按钮的高度，滚到底时最后一项不会被压住
                .padding(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(topInset))
            Text(
                text = stringResource(R.string.bank_card_select_bank),
                style = MaterialTheme.typography.titleMedium
            )

            uiState.banks.forEach { bank ->
                BankOptionRow(
                    bank = bank,
                    selected = bank.code == uiState.selectedBankCode,
                    enabled = !uiState.isSubmitting,
                    onClick = { onSelectBank(bank.code) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                status?.let { currentStatus ->
                    if (currentStatus.retmsg.isNotBlank()) {
                        Text(
                            text = currentStatus.retmsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (currentStatus.isBound) {
                                Color(0xFF2E7D32)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!showUnbind) {
                TextField(
                    value = uiState.cardNo,
                    onValueChange = onCardNoChange,
                    label = { Text(stringResource(R.string.bank_card_no_label)) },
                    placeholder = { Text(stringResource(R.string.bank_card_no_placeholder)) },
                    singleLine = true,
                    enabled = !uiState.isSubmitting,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

        }

        AppSaveFab(
            onClick = {
                val canSubmit = !uiState.isSubmitting &&
                    (showUnbind || uiState.cardNo.isNotBlank())
                if (canSubmit) {
                    if (showUnbind) onUnbindClick() else onBind()
                }
            },
            icon = if (showUnbind) Icons.Outlined.LinkOff else Icons.Outlined.Link,
            text = if (showUnbind) {
                stringResource(R.string.bank_card_unbind)
            } else {
                stringResource(R.string.bank_card_bind)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun BankOptionRow(
    bank: BankOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // 先裁圆角再挂点击，水波纹才会跟着圆角走（Card 的 shape 只管背景与边框）
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bank.iconUrl.isNotBlank()) {
                AsyncImage(
                    model = bank.iconUrl,
                    contentDescription = bank.code,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = bank.code,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = bank.code,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = if (selected) {
                    Icons.Outlined.RadioButtonChecked
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
