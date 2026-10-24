package edu.cqwu.electricity.speakup.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.speakup.data.ConsultationMessage
import edu.cqwu.electricity.common.ui.BlurPullRefreshIndicator
import edu.cqwu.electricity.common.ui.PagingFooter
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.common.ui.BlurScaffold
import edu.cqwu.electricity.common.ui.transparentTopBarColors

/**
 * 留言列表页面。
 *
 * 显示指定咨询区的留言列表，支持分页加载和下拉刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageListScreen(
    areaCode: String,
    areaName: String,
    onBack: () -> Unit,
    onMessageClick: (String) -> Unit,
    onReLogin: () -> Unit = {},
    viewModel: MessageListViewModel = viewModel(
        factory = MessageListViewModel.Factory(areaCode, areaName)
    ),
) {
    val topBarColors = transparentTopBarColors()
    val uiState by viewModel.uiState.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val listState = rememberLazyListState()

    // 检测滚动到底部，自动加载更多
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    BlurScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.speakup_messages_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                colors = topBarColors,
            )
        }
    ) { topInset ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = uiState is MessageListViewModel.UiState.Loading,
            onRefresh = { viewModel.refresh() },
            state = pullState,
            indicator = { BlurPullRefreshIndicator(pullState, uiState is MessageListViewModel.UiState.Loading, topInset) },
            modifier = Modifier.fillMaxSize()
        ) {
            when (val state = uiState) {
                is MessageListViewModel.UiState.Loading -> {
                    // PullToRefreshBox 已显示下拉刷新指示器
                }

                is MessageListViewModel.UiState.Error -> {
                    ReLoginContent(
                        errorMessage = state.message,
                        requiresReLogin = state.requiresReLogin,
                        onReLogin = onReLogin,
                        onRetry = { viewModel.refresh() },
                    )
                }

                is MessageListViewModel.UiState.Success -> {
                    if (state.messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.speakup_no_messages),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item(key = "top_inset") { Spacer(Modifier.height(topInset)) }
                            items(
                                items = state.messages,
                                key = { it.wid }
                            ) { message ->
                                MessageCard(
                                    message = message,
                                    onClick = { onMessageClick(message.wid) }
                                )
                            }

                            // 分页提示：加载中 / 没有更多（统一组件；本页只有自动加载，不传 onLoadMore）
                            if (isLoadingMore || (!state.hasMore && state.messages.isNotEmpty())) {
                                item {
                                    PagingFooter(isLoadingMore = isLoadingMore, hasMore = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单条留言卡片。
 *
 * 外层是与其他列表一致的无阴影 1dp 描边卡片；卡片内的信息位置保持不变：
 * - 第一行：标题（加粗）+ 右侧时间
 * - 第二行：学院 姓名 + 右侧咨询区名称
 */
@Composable
private fun MessageCard(
    message: ConsultationMessage,
    onClick: () -> Unit,
) {
    // 用 Card（ElevatedCard 没有 border 参数）；无阴影，轮廓由 1dp 描边提供
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            // 先裁圆角再挂点击，水波纹才会跟着圆角走（Card 的 shape 只管背景与边框）
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 第一行：标题 + 时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.zxbt,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = message.zxsj,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 第二行：学院 姓名 + 咨询区名称
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${message.zxrdwdmDisplay} ${message.zxrxm}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = message.zxqdmDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
