package edu.cqwu.electricity.person.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import edu.cqwu.electricity.R
import edu.cqwu.electricity.person.data.PersonRow
import edu.cqwu.electricity.person.data.PersonSearchApi
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.theme.ui.currentTopBarColors

/**
 * 查找人员页面。
 *
 * 顶部搜索条（下划线样式）输入姓名关键字，点击行尾搜索图标或键盘搜索键查询；
 * 进入页面自动搜索一次（空词 → 全部人员）；统计行显示搜索人数与页码；
 * 支持下拉刷新与滚动到底自动加载下一页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonSearchScreen(
    onBack: () -> Unit,
    onReLogin: () -> Unit = {},
    viewModel: PersonSearchViewModel = viewModel(),
) {
    val topBarColors = currentTopBarColors()
    val uiState by viewModel.uiState.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val keyword by viewModel.keyword.collectAsState()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.person_search_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = topBarColors,
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── 搜索条（下划线样式）+ 行尾搜索图标（点击图标搜索） ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = keyword,
                    onValueChange = { viewModel.onKeywordChange(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.person_search_hint)) },
                    trailingIcon = {
                        if (keyword.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearKeyword() }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.common_clear_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                )

                // 行尾搜索图标（无背景，点击触发搜索）
                IconButton(onClick = { viewModel.search() }) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.person_search_button),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── 统计信息行：左「共搜索到 x 人」右「第 n/总页数 页」（排版参照账单页 BillStatsRow） ──
            val currentState = uiState
            if (currentState is PersonSearchViewModel.UiState.Success && currentState.rows.isNotEmpty()) {
                PersonStatsRow(
                    totalSize = currentState.totalSize,
                    currentPage = currentState.currentPage,
                    totalPages = currentState.totalPages,
                )
            }

            PullToRefreshBox(
                isRefreshing = uiState is PersonSearchViewModel.UiState.Loading,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (val state = uiState) {
                    is PersonSearchViewModel.UiState.Idle -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.person_search_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is PersonSearchViewModel.UiState.Loading -> {
                        // 加载中不显示中心转圈，保持空白（下拉刷新指示器仍可用）
                        Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
                    }

                    is PersonSearchViewModel.UiState.Error -> {
                        ReLoginContent(
                            errorMessage = state.message,
                            requiresReLogin = state.requiresReLogin,
                            onReLogin = onReLogin,
                            onRetry = { viewModel.refresh() },
                        )
                    }

                    is PersonSearchViewModel.UiState.Success -> {
                        if (state.rows.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.person_no_result),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                            ) {
                                items(
                                    items = state.rows,
                                    key = { it.id ?: it.hashCode().toString() },
                                ) { person ->
                                    PersonCard(person = person)
                                }

                                // 加载更多指示器
                                if (isLoadingMore) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        }
                                    }
                                }

                                // 没有更多数据
                                if (!state.hasMore) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = stringResource(R.string.person_no_more),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
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
 * 单条人员卡片。
 *
 * 布局：左侧头像（headPic.do，加载失败显示圆形底色）+ 右侧姓名/性别/工号+部门编码 + 部门 + 职务。
 * 本期仅展示，不做点击二级跳转。
 */
@Composable
private fun PersonCard(person: PersonRow) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── 左侧头像（加载失败时仅显示圆形底色，不显示兜底图标） ──
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val personId = person.id
                if (!personId.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(PersonSearchApi.headPicUrl(personId))
                            .size(128)
                            .crossfade(true)
                            .build(),
                        contentDescription = person.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // ── 右侧信息 ──
            Column(modifier = Modifier.weight(1f)) {
                // 第一行：姓名 + 性别 + 长/短数字（id 工号、deptCode 部门编码）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = person.name ?: person.id ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!person.sexName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = person.sexName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // id（长数字）与 deptCode（短数字）之间用空格隔开
                    val numbers = listOfNotNull(person.id, person.deptCode)
                    if (numbers.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = numbers.joinToString(" "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 第二行：部门/学院（或工号兜底）
                Text(
                    text = person.deptName ?: person.id ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // 第三行：职务（可空）
                if (!person.positions.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = person.positions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(start = 72.dp),
        )
    }
}

/**
 * 统计信息行：左「共搜索到 x 人」右「第 n/总页数 页」。
 * 排版参照账单页 [edu.cqwu.electricity.cardcenter.ui.BillScreen] 的 BillStatsRow：
 * fillMaxWidth + padding(8,16) + SpaceBetween，bodySmall / onSurfaceVariant。
 */
@Composable
private fun PersonStatsRow(
    totalSize: Int,
    currentPage: Int,
    totalPages: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = pluralStringResource(R.plurals.person_search_total_count, totalSize, totalSize),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.person_page_info, currentPage, totalPages),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
