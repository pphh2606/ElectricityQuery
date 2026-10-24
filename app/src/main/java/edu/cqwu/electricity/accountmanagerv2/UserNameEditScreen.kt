package edu.cqwu.electricity.accountmanagerv2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import edu.cqwu.electricity.common.ui.BlurPullRefreshIndicator
import edu.cqwu.electricity.common.ui.AppOutlinedTextField
import edu.cqwu.electricity.common.ui.LoadingDialog
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.common.ui.CollapsibleScaffold
import edu.cqwu.electricity.common.util.ToastUtils

/**
 * 修改用户名页（登录别名 + 昵称）— 布局对应 CAS 网页 mobileUserAttrEdit.do：
 * 顶部提示文字 → 登录别名 / 昵称（12dp 圆角轮廓输入框）→ 右下角浮动保存按钮。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    CollapsibleScaffold(
        title = stringResource(R.string.user_name_edit_title),
        onBack = onBack,
        // 键盘弹出时让出 IME 高度，避免遮挡右下角"保存"按钮
        modifier = Modifier.fillMaxSize().imePadding(),
    ) { topInset, scrollModifier ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            state = pullState,
            indicator = { BlurPullRefreshIndicator(pullState, state.isRefreshing, topInset) },
            modifier = Modifier.fillMaxSize(),
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
                // 保存按钮不再占一整行：改成 Box 覆盖层里的浮动按钮（同 InstallerX 的做法），
                // 内容铺满整屏、可滚到按钮下方，背后不会留出一条遮挡横带。
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(scrollModifier)
                            .verticalScroll(rememberScrollState())
                            // 底部预留浮动按钮的高度，滚到底时最后一项不会被按钮压住
                            .padding(top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Spacer(modifier = Modifier.height(topInset))
                        // 对应网页 form-tip
                        Text(
                            text = stringResource(R.string.user_name_edit_tip),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // 对应网页 form-group：登录别名
                        AppOutlinedTextField(
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
                        )

                        // 对应网页 form-group：昵称
                        AppOutlinedTextField(
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
                        )
                    }

                    // 对应网页 form-footer：保存（右下角浮动，同 InstallerX 的 SmallExtendedFloatingActionButton）
                    SmallExtendedFloatingActionButton(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Save,
                                contentDescription = null,
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.user_name_edit_save),
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        onClick = { if (!state.isSaving) viewModel.save() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 16.dp),
                    )
                }
            }
        }
    }

    if (state.isSaving) {
        LoadingDialog(message = stringResource(R.string.user_name_edit_saving))
    }
}
