@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package edu.cqwu.electricity.speakup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.common.ui.ScrollLinkedBox
import edu.cqwu.electricity.R
import edu.cqwu.electricity.speakup.data.ConsultationArea
import edu.cqwu.electricity.common.ui.AppPullToRefresh
import edu.cqwu.electricity.common.ui.BottomSheetDialogV2
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.common.ui.CollapsibleScaffold
import kotlinx.coroutines.launch

/**
 * 发布留言 WebView URL 模板。
 * `{ZXQDM}` 会被替换为具体的咨询区代码。
 */
private const val PUBLISH_URL_TEMPLATE =
    "https://ehall.cqwu.edu.cn/qljfwappnew/sys/lwPsZxzxApp/*default/index.do#/fbzx?ZXQDM={ZXQDM}"

/**
 * 「有话要说」页面 — 咨询区列表。
 *
 * 布局：
 * - TopAppBar：返回箭头 + 标题「有话要说」
 * - 下拉刷新列表，每项显示科室名称 + 箭头
 * - 点击科室 → 弹出 BottomSheetDialog，提供「发布留言」和「留言浏览」两个选项
 * - 「发布留言」→ 跳转内置 WebView
 * - 「留言浏览」→ Snackbar 提示开发中
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakUpScreen(
    onBack: () -> Unit,
    onNavigateToWebView: (url: String, title: String) -> Unit,
    onNavigateToMessages: (areaCode: String, areaName: String) -> Unit = { _, _ -> },
    onReLogin: () -> Unit = {},
    viewModel: SpeakUpViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // 弹窗状态
    var showActionDialog by remember { mutableStateOf(false) }
    var selectedArea by remember { mutableStateOf<ConsultationArea?>(null) }

    CollapsibleScaffold(
        title = stringResource(R.string.speakup_title),
        onBack = onBack,
    ) { topInset, scrollModifier ->
        AppPullToRefresh(
            isRefreshing = uiState is SpeakUpViewModel.UiState.Loading,
            onRefresh = { viewModel.refresh() },
            topInset = topInset,
            modifier = Modifier.fillMaxSize()
        ) {
            ScrollLinkedBox(scrollModifier) {
            when (val state = uiState) {
                is SpeakUpViewModel.UiState.Loading -> {
                    // AppPullToRefresh 已显示下拉刷新指示器，内容区留空
                }

                is SpeakUpViewModel.UiState.Error -> {
                    ReLoginContent(
                        errorMessage = state.message,
                        requiresReLogin = state.requiresReLogin,
                        onReLogin = onReLogin,
                        onRetry = { viewModel.refresh() },
                    )
                }

                is SpeakUpViewModel.UiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        item(key = "top_inset") { Spacer(Modifier.height(topInset)) }
                        items(
                            items = state.areas,
                            key = { it.wid }
                        ) { area ->
                            ConsultationAreaItem(
                                area = area,
                                onClick = {
                                    selectedArea = area
                                    showActionDialog = true
                                }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
            }
        }
    }

    // ── 操作选择弹窗 ──
    BottomSheetDialogV2(
        visible = showActionDialog,
        onDismissRequest = { showActionDialog = false },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 发布留言按钮
            Button(
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    val area = selectedArea
                    showActionDialog = false
                    if (area != null) {
                        scope.launch {
                            // 先预设 ehall 角色，确保 WebView 加载时 menu.do 返回正确页面
                            viewModel.preSetupRole()
                            val url = PUBLISH_URL_TEMPLATE.replace("{ZXQDM}", area.zxqdm)
                            onNavigateToWebView(url, area.zxqmc)
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.speakup_publish),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }

            // 留言浏览按钮
            Button(
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    val area = selectedArea
                    showActionDialog = false
                    if (area != null) {
                        onNavigateToMessages(area.zxqdm, area.zxqmc)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.speakup_browse),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 取消按钮
        TextButton(
            shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(16.dp)),
            onClick = { showActionDialog = false },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = ButtonDefaults.textButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text(
                text = stringResource(R.string.common_cancel),
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

/**
 * 单个咨询区列表项：图标底板 + 名称 + 右箭头，与缴费服务大厅的行样式一致。
 *
 * 咨询区数据里没有图标，统一用内置的 [Icons.Outlined.Forum] 放在 surfaceVariant 底板上，
 * 图标取主题色；名称不限行数，长科室名换行完整显示，不截断。
 */
@Composable
private fun ConsultationAreaItem(
    area: ConsultationArea,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Forum,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = area.zxqmc,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
