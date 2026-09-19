package edu.cqwu.electricity.notice.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.notice.data.NoticeItem
import edu.cqwu.electricity.common.ui.PagingFooter
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 通知公告列表页（常驻搜索栏）
 *
 * 状态由 [NoticeViewModel] 管理，导航切换时不重新加载。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeScreen(
    viewModel: NoticeViewModel,
    onBack: () -> Unit,
    onNavigateToNoticeDetail: (wid: String) -> Unit,
    onReLogin: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var searchText by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val topBarColors = currentTopBarColors()

    // 每次从首页进入时刷新；从详情返回时不刷新
    // 同时清除上次的搜索状态，避免搜索残留
    LaunchedEffect(Unit) {
        searchText = ""
        isSearching = false
        if (viewModel.listRefreshEnabled || viewModel.items.isEmpty()) {
            viewModel.clearSearch()
            viewModel.loadPage(0, isRefresh = true)
            viewModel.listRefreshEnabled = false
        }
    }

    // 无限滚动
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex to totalItemsCount to viewModel.hasMore
        }
            .distinctUntilChanged()
            .collect { (_, hasMoreData) ->
                val layoutInfo = listState.layoutInfo
                val totalItemsCount = layoutInfo.totalItemsCount
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val isNearBottom = totalItemsCount > 0 && lastVisibleIndex >= totalItemsCount - 3

                if (isNearBottom && hasMoreData && !viewModel.isLoadingMore && !viewModel.isLoading) {
                    val keyword = viewModel.searchKeyword.ifBlank { null }
                    viewModel.loadPage(viewModel.currentPage + 1, keyword = keyword)
                }
            }
    }

    // 执行搜索：立即清空旧数据并加载搜索结果
    fun doSearch(keyword: String) {
        viewModel.search(keyword)
        scope.launch {
            viewModel.loadPage(0, isRefresh = true, keyword = keyword.ifBlank { null })
        }
    }

    // 退出搜索态：只收起搜索条，不改当前查询条件
    fun exitSearch() {
        focusManager.clearFocus()
        isSearching = false
    }

    LaunchedEffect(isSearching) {
        if (isSearching) focusRequester.requestFocus()
    }

    // 搜索态下拦截系统返回键：先退出搜索，而不是退出页面
    BackHandler(enabled = isSearching) { exitSearch() }

    // 软键盘回车与右侧搜索按钮都走这里：提交关键词并退出搜索态
    fun submitSearch() {
        val keyword = searchText.trim()
        exitSearch()
        doSearch(keyword)
    }

    Scaffold(
        topBar = {
            if (isSearching) {
                // ── 搜索态：标题栏整体变成搜索条 ──
                TopAppBar(
                    title = {
                        TextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.notice_search_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                            trailingIcon = {
                                if (searchText.isNotEmpty()) {
                                    // 只清输入框，不立刻改查询条件——提交后才生效
                                    IconButton(onClick = { searchText = "" }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = stringResource(R.string.common_clear_search),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        // 取消：不改查询条件，直接退出搜索态
                        IconButton(onClick = { exitSearch() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_exit_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { submitSearch() }) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.common_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = topBarColors
                )
            } else {
                // ── 普通态：标题（含总数）+ 右侧搜索按钮 ──
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.notice_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (viewModel.totalItem > 0) {
                                Text(
            text = pluralStringResource(R.plurals.notice_total_count, viewModel.totalItem, viewModel.totalItem),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
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
                    actions = {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.common_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = topBarColors
                )
            }
        }
    ) { paddingValues ->
        when {
            viewModel.isLoading && viewModel.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (viewModel.searchKeyword.isNotBlank()) stringResource(R.string.notice_searching) else stringResource(R.string.notice_loading_list),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            (viewModel.errorMessage != null || viewModel.requiresReLogin) && viewModel.items.isEmpty() -> {
                ReLoginContent(
                    errorMessage = viewModel.errorMessage,
                    requiresReLogin = viewModel.requiresReLogin,
                    onReLogin = onReLogin,
                    onRetry = {
                        scope.launch {
                            val keyword = viewModel.searchKeyword.ifBlank { null }
                            viewModel.loadPage(0, keyword = keyword)
                        }
                    },
                    modifier = Modifier.padding(paddingValues),
                )
            }

            else -> {
                PullToRefreshBox(
                    isRefreshing = viewModel.isLoading,
                    onRefresh = {
                        scope.launch {
                            val keyword = viewModel.searchKeyword.ifBlank { null }
                            viewModel.loadPage(0, isRefresh = true, keyword = keyword)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 搜索模式：显示搜索结果标题
                        if (viewModel.searchKeyword.isNotBlank() && viewModel.items.isNotEmpty()) {
                            item(key = "search_header") {
                                Text(
            text = pluralStringResource(R.plurals.notice_search_result, viewModel.totalItem, viewModel.searchKeyword, viewModel.totalItem),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }

                        items(viewModel.items, key = { it.wid }) { notice ->
                            NoticeCard(
                                notice = notice,
                                onClick = { onNavigateToNoticeDetail(notice.wid) }
                            )
                        }

                        item(key = "footer") {
                            // 本页只有自动分页加载（NoticeViewModel 没有公开的手动加载入口），故不传 onLoadMore
                            val showFooter = viewModel.isLoadingMore ||
                                (viewModel.items.isNotEmpty() && !viewModel.hasMore)
                            if (showFooter) {
                                PagingFooter(isLoadingMore = viewModel.isLoadingMore, hasMore = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(
    notice: NoticeItem,
    onClick: () -> Unit
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = notice.noticeTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                thickness = 0.5.dp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NoticeInfoChip(icon = Icons.Outlined.Person, text = notice.sendDepartment.ifBlank { stringResource(R.string.dashboard_unknown) })
                NoticeInfoChip(icon = Icons.Outlined.AccessTime, text = notice.sendTimeDesc)
                Spacer(modifier = Modifier.weight(1f))
                NoticeInfoChip(icon = Icons.Outlined.Visibility, text = notice.clickNumber)
            }
        }
    }
}

@Composable
private fun NoticeInfoChip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
