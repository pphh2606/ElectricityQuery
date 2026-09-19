@file:OptIn(ExperimentalMaterial3Api::class)

package edu.cqwu.electricity.jwxt.score.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.navigation.LocalNavController
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.common.ui.SectionFilterChip
import edu.cqwu.electricity.jwxt.core.JwxtConstants
import edu.cqwu.electricity.jwxt.score.data.JwxtScore
import edu.cqwu.electricity.jwxt.score.data.JwxtScoreTerm
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import kotlinx.coroutines.launch

/**
 * 成绩查询（本地化 jwfw `/jwmobile` 的 `kwApp/cjcx_v5` 页）。
 *
 * 入口：教务首页九宫格的「成绩查询」格子。结构：顶栏 → 学期胶囊 → 三个 Tab（可点可滑）→ 成绩列表，
 * 点一条成绩用内置浏览器打开它的详情网页。网页上的顶部统计卡片与评教跳转不做：前者接口实测恒返回空数组，
 * 后者该字段在抓包里全为 false，没有可验证的目标。
 */
@Composable
fun JwxtScoreScreen(
    onBack: () -> Unit,
    viewModel: JwxtScoreViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val nav = LocalNavController.current
    val title = stringResource(R.string.jwxt_score_title)
    val scope = rememberCoroutineScope()

    // 三个 Tab 之间左右滑动。当前页不必自己记：rememberPagerState 内部用的是「可跨页面保存的记住」，
    // 从内置浏览器（成绩详情）返回时会自动回到离开时那一页。
    val filters = JwxtScoreFilter.entries
    val pagerState = rememberPagerState(pageCount = { filters.size })
    // 每个 Tab 各自一份滚动状态，互不干扰（与项目其它 Pager 页一致）
    val listStates = remember { List(filters.size) { LazyListState() } }

    // 切换学期：Tab 复位到「全部」、各页列表回到顶部（网页端切学期也是这个行为）。
    // 首次组合时学期码还是空的、当前页本来就是 0，这段等于空转，不会干扰进入页面时的状态。
    LaunchedEffect(uiState.selectedTermCode) {
        if (pagerState.currentPage != 0) pagerState.scrollToPage(0)
        listStates.forEach { it.scrollToItem(0) }
    }

    // 从登录页返回时自动重试一次（仅在确实因会话过期失败时），与教务首页同一套处理
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && viewModel.uiState.value.requiresReLogin) {
                viewModel.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    // 打开网页版：保留一条回到教务原网页的退路（与教务首页、更多服务页顶栏一致）
                    IconButton(onClick = { nav.navigate(Routes.unifiedWebViewRoute(JwxtConstants.SCORE_URL, title)) }) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInBrowser,
                            contentDescription = stringResource(R.string.common_web_version),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = currentTopBarColors(),
            )
        },
    ) { paddingValues ->
        when {
            uiState.requiresReLogin -> ReLoginContent(
                requiresReLogin = true,
                onReLogin = { nav.navigate(Routes.loginRoute()) },
                modifier = Modifier.padding(paddingValues),
            )

            uiState.errorMessage != null -> ReLoginContent(
                errorMessage = uiState.errorMessage,
                requiresReLogin = false,
                onReLogin = {},
                onRetry = { viewModel.load() },
                modifier = Modifier.padding(paddingValues),
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // 学期胶囊：选定后固定在上方，只切换下面的成绩内容（加载中先不显示，避免空行跳一下）
                if (uiState.terms.isNotEmpty()) {
                    TermChips(
                        terms = uiState.terms,
                        selectedTermCode = uiState.selectedTermCode,
                        onSelect = { viewModel.selectTerm(it) },
                    )
                }

                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    divider = {},
                ) {
                    filters.forEachIndexed { index, filter ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(text = filterLabel(filter)) },
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { page ->
                    ScorePage(
                        scores = uiState.scoresFor(filters[page]),
                        listState = listStates[page],
                        isLoading = uiState.isLoading,
                        // 首次加载也算「正在刷新」，让顶部下拉指示器一进页面就出现
                        // （与教务首页同一套处理）；三个 Tab 共用一份数据，指示器只画在当前页
                        isRefreshing = (uiState.isLoading || uiState.isRefreshing) &&
                            page == pagerState.currentPage,
                        onRefresh = { viewModel.refresh() },
                        onScoreClick = { score ->
                            nav.navigate(
                                Routes.unifiedWebViewRoute(
                                    JwxtConstants.scoreDetailUrl(score.id),
                                    score.courseName,
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * 一个 Tab 的成绩列表。
 *
 * 下拉刷新放在**页内**：它和外层 Pager 的横向手势、列表的纵向滚动在同一区域，
 * 放到 Pager 外面会互相抢手势（项目其它 Pager 页也是这个层级）。
 * 首屏加载态用顶部下拉指示器，数据没到时留白——只有确实没有成绩才显示「暂无成绩」。
 */
@Composable
private fun ScorePage(
    scores: List<JwxtScore>,
    listState: LazyListState,
    isLoading: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onScoreClick: (JwxtScore) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            scores.isNotEmpty() -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(scores) { score ->
                    ScoreItem(score = score, onClick = { onScoreClick(score) })
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp,
                    )
                }
            }

            // 数据还没到：留白即可（顶部下拉指示器在转），此时不能说「暂无成绩」。
            // 用空的可滚动容器而不是普通 Box——下拉手势要靠子容器的滚动事件传上来，普通 Box 拉不动
            isLoading -> LazyColumn(modifier = Modifier.fillMaxSize()) {}

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Text(
                        text = stringResource(R.string.jwxt_score_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 学期胶囊行：横向滚动单选，按钮样式复用 `common/ui` 的圆形筛选按钮 */
@Composable
private fun TermChips(
    terms: List<JwxtScoreTerm>,
    selectedTermCode: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        // 顶部不留间距，让学期胶囊紧贴顶栏（与首页分区索引栏的规格一致）；
        // 底部保留 4dp，它下面紧接 Tab 切换行，完全贴合会挤在一起
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(terms) { term ->
            SectionFilterChip(
                text = term.termName,
                selected = term.termCode == selectedTermCode,
                onClick = { onSelect(term.termCode) },
            )
        }
    }
}

/** Tab 文案；枚举顺序即显示顺序 */
@Composable
private fun filterLabel(filter: JwxtScoreFilter): String = stringResource(
    when (filter) {
        JwxtScoreFilter.ALL -> R.string.jwxt_score_tab_all
        JwxtScoreFilter.PASSED -> R.string.jwxt_score_tab_passed
        JwxtScoreFilter.NOT_PASSED -> R.string.jwxt_score_tab_not_passed
    }
)

/**
 * 一条成绩：左课程名 + 属性标签 + 学期名，右成绩，点击打开它的详情网页。
 *
 * 结构照网页条目（左信息、右成绩、行间细分割线），颜色用主题语义色：通过 = primary、
 * 未通过 = error；接口没给通过状态（`passFlag` 为 null）时不着色，与网页一致。
 */
@Composable
private fun ScoreItem(score: JwxtScore, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 接口标明没有详情页的记录不给点击反馈——网页此时同样不跳转，避免"点了没反应"的错觉
            .then(if (score.isToDetail) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = score.courseName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            ScoreTags(score)

            Text(
                text = score.termName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = score.score,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = when (score.passFlag) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * 属性标签行：学分 / 课程类别 / 修读方式 / 考核方式（顺序与网页一致），空值整段不显示。
 * 用 [FlowRow] 换行——课程类别最长（如「学科基础必修课」），窄屏一行放不下四个标签。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreTags(score: JwxtScore) {
    val tags = listOfNotNull(
        // 接口字段名是 coursePoint，但网页按「X学分」显示（实测出现过 0.25）
        score.coursePoint.takeIf { it.isNotBlank() }?.let { stringResource(R.string.jwxt_score_credit, it) },
        score.courseTypeName.takeIf { it.isNotBlank() },
        score.examProp.takeIf { it.isNotBlank() },
        score.examType.takeIf { it.isNotBlank() },
    )
    if (tags.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            // 单个标签；规格对齐今日课程卡里的「调 / 补」标签（圆角小底色块）
            Text(
                text = tag,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
