package edu.cqwu.electricity.accountmanagerv2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.ui.LoadingDialog
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import edu.cqwu.electricity.theme.util.ToastUtils

/**
 * 修改用户名页（登录别名 + 昵称）— 布局对应 CAS 网页 mobileUserAttrEdit.do：
 * 顶部提示文字 → 登录别名 / 昵称（下划线输入框）→ 右下角保存按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserNameEditScreen(
    onBack: () -> Unit,
    onReLogin: () -> Unit,
    viewModel: UserNameEditViewModel = viewModel(),
) {
    val snackbar = LocalSnackbarController.current
    val state by viewModel.uiState.collectAsState()

    // 保存结果消息（服务端 returnValue），展示后消费并刷新
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let {
            snackbar.show(it, if (state.savedSuccess) ToastUtils.Type.SUCCESS else ToastUtils.Type.ERROR)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.user_name_edit_title), fontWeight = FontWeight.Bold) },
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
                state.loadError != null && state.alias.isEmpty() -> ReLoginContent(
                    errorMessage = state.loadError,
                    requiresReLogin = false,
                    onReLogin = {},
                    onRetry = viewModel::refresh,
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // 对应网页 form-tip
                        Text(
                            text = stringResource(R.string.user_name_edit_tip),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // 对应网页 form-group：登录别名
                        TextField(
                            value = state.alias,
                            onValueChange = viewModel::onAliasChange,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSaving,
                            singleLine = true,
                            label = { Text(stringResource(R.string.user_name_edit_alias_label)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            isError = state.aliasError != null,
                            supportingText = {
                                Text(
                                    text = when {
                                        state.aliasError != null -> stringResource(R.string.user_name_edit_alias_unavailable)
                                        state.aliasChecked -> stringResource(R.string.user_name_edit_alias_available)
                                        else -> ""
                                    },
                                    color = if (state.aliasError != null) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        Color(0xFF2E7D32)
                                    },
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                        )

                        // 对应网页 form-group：昵称
                        TextField(
                            value = state.nickName,
                            onValueChange = viewModel::onNickNameChange,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSaving,
                            singleLine = true,
                            label = { Text(stringResource(R.string.user_name_edit_nickname_label)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Badge,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            placeholder = { Text(stringResource(R.string.user_name_edit_nickname_placeholder)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                        )
                    }

                    // 对应网页 form-footer：保存（右下角固定，参考学生绑定银行卡）
                    Button(
                        onClick = viewModel::save,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(bottom = 16.dp),
                        enabled = !state.isSaving,
                    ) {
                        Text(
                            text = stringResource(R.string.user_name_edit_save),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    if (state.isSaving) {
        LoadingDialog(message = stringResource(R.string.user_name_edit_saving))
    }
}
