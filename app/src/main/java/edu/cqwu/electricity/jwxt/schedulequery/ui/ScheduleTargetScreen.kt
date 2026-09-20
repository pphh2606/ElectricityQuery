@file:OptIn(ExperimentalMaterial3Api::class)

package edu.cqwu.electricity.jwxt.schedulequery.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.navigation.LocalNavController
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.common.ui.BottomSheetDialogV2
import edu.cqwu.electricity.common.ui.BottomSheetItem
import edu.cqwu.electricity.common.ui.LoadingDialog
import edu.cqwu.electricity.common.ui.ListStatsRow
import edu.cqwu.electricity.common.ui.PagingFooter
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.common.ui.SectionFilterChip
import edu.cqwu.electricity.jwxt.core.JwxtConstants
import edu.cqwu.electricity.jwxt.schedulequery.data.JwxtDictItem
import edu.cqwu.electricity.jwxt.core.model.JwxtScheduleTarget
import edu.cqwu.electricity.jwxt.core.model.ScheduleTargetType
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import kotlinx.coroutines.launch

/** 一个筛选胶囊：显示文本 + 点开后弹出的选项 */
private data class FilterEntry(
    val label: String,
    val title: String,
    val options: List<JwxtDictItem>,
    val selectedId: String,
    val onSelect: (String) -> Unit,
    /** 是否提供「全部」选项（学期没有"全部"，必须选一个） */
    val allowAll: Boolean = true,
)

/**
 * 「全校课表查询」的选择页：教室 / 教师 / 班级三种界面**共用这一个页面**，按 [type] 渲染各自的筛选条件。
 *
 * 网页端同样是「同一套页面组件 + 不同样式类」——抓包实证：jaskb / jskb / bjkb 三个 chunk 引用的 21 个模块里
 * 20 个完全相同，唯一不同的那一个就是各自的 CSS 类名表。所以本地也用参数化，而不是写三个页面。
 *
 * 点某一项之后要先查「它在哪个校区」再进课表页（实测校区与对象不匹配时课表接口返回空列表），
 * 这一步走 [JwxtScheduleQueryViewModel.fetchTargetWithCampus]。
 */
@Composable
fun JwxtScheduleTargetScreen(
    type: ScheduleTargetType,
    onBack: () -> Unit,
    onOpenTable: (JwxtScheduleTarget) -> Unit,
    viewModel: JwxtScheduleQueryViewModel = viewModel(factory = JwxtScheduleQueryViewModel.Factory(type)),
) {
    val state by viewModel.uiState.collectAsState()
    val nav = LocalNavController.current
    val scope = rememberCoroutineScope()
    val title = stringResource(type.titleRes)

    // 正在「查校区 → 进课表」的过程中：禁用重复点击
    var opening by remember { mutableStateOf(false) }
    // 当前打开的筛选弹窗；null 表示没打开
    var picker by remember { mutableStateOf<FilterEntry?>(null) }

    // 搜索放在标题栏上（与首页搜索同一套做法）；班级页不支持——接口没有按班级名搜索的能力
    val searchable = type != ScheduleTargetType.CLASS
    val searchHint = stringResource(
        if (type == ScheduleTargetType.CLASSROOM) R.string.jwxt_schedule_search_classroom
        else R.string.jwxt_schedule_search_teacher
    )
    var isSearching by remember { mutableStateOf(false) }
    // 草稿：只有回车或点右侧搜索按钮才真正查询，不做「输入即搜」
    var searchInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // 进入搜索态自动聚焦，并把上次用的关键词带回来
    LaunchedEffect(isSearching) {
        if (isSearching) {
            searchInput = state.keyword
            focusRequester.requestFocus()
        }
    }
    // 搜索态下拦截系统返回键：先退出搜索，而不是退出页面
    BackHandler(enabled = isSearching) {
        focusManager.clearFocus()
        isSearching = false
    }
    fun submitSearch() {
        focusManager.clearFocus()
        isSearching = false
        viewModel.setKeyword(searchInput.trim())
    }

    Scaffold(
        topBar = {
            if (isSearching) {
                // ── 搜索态：标题栏整体变成搜索条 ──
                TopAppBar(
                    title = {
                        TextField(
                            value = searchInput,
                            onValueChange = { searchInput = it },
                            placeholder = {
                                Text(
                                    text = searchHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                if (searchInput.isNotEmpty()) {
                                    IconButton(onClick = { searchInput = "" }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Clear,
                                            contentDescription = stringResource(R.string.common_clear_search),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                        )
                    },
                    navigationIcon = {
                        // 取消：不改查询条件，直接退出搜索态
                        IconButton(onClick = {
                            focusManager.clearFocus()
                            isSearching = false
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_exit_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        // 软键盘回车或点这里，才真正发起搜索
                        IconButton(onClick = { submitSearch() }) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.common_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = currentTopBarColors(),
                )
            } else {
                // ── 普通态 ──
                TopAppBar(
                    title = { Text(text = title, fontWeight = FontWeight.Bold) },
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
                        if (searchable) {
                            IconButton(onClick = { isSearching = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = stringResource(R.string.common_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { nav.navigate(Routes.unifiedWebViewRoute(JwxtConstants.SCHEDULE_QUERY_URL, title)) }) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInBrowser,
                                contentDescription = stringResource(R.string.common_web_version),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = currentTopBarColors(),
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            TargetFilterBar(type = type, state = state, viewModel = viewModel, onPick = { picker = it })
            // 列表统计行（已加载条数 + 页码）：有了它就不再需要标题栏下那条分隔线
            if (state.pageTotal > 0) {
                ListStatsRow(
                    loadedCount = state.rows.size,
                    currentPage = state.pageNumber,
                    totalPages = state.pageTotal,
                )
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.requiresReLogin -> ReLoginContent(
                        requiresReLogin = true,
                        onReLogin = { nav.navigate(Routes.loginRoute()) },
                    )

                    state.loadError != null && state.rows.isEmpty() -> ReLoginContent(
                        errorMessage = state.loadError,
                        requiresReLogin = false,
                        onReLogin = {},
                        onRetry = viewModel::refresh,
                    )

                    // 首屏加载中：内容区留白——转圈交给下拉刷新的顶部指示器，这里不再放第二个居中的
                    state.isRefreshing && state.rows.isEmpty() -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {}

                    state.rows.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.jwxt_schedule_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> ScheduleRowList(
                        state = state,
                        onLoadMore = viewModel::loadMore,
                        onRowClick = { row ->
                            if (!opening) {
                                opening = true
                                scope.launch {
                                    viewModel.fetchTargetWithCampus(row)?.let(onOpenTable)
                                    opening = false
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    // 查校区期间挡住交互：这一步要联网（校区与对象不匹配时课表接口返回空列表），
    // 弹窗把这段等待填住；成功或失败都会把 opening 复位，弹窗随之消失
    if (opening) {
        LoadingDialog(message = stringResource(R.string.jwxt_schedule_opening))
    }

    // 筛选弹窗：同一个弹窗按当前点的胶囊列不同的项
    picker?.let { entry ->
        BottomSheetDialogV2(
            visible = true,
            onDismissRequest = { picker = null },
            title = entry.title,
        ) {
            if (entry.allowAll) {
                BottomSheetItem(
                    icon = null,
                    title = stringResource(R.string.jwxt_schedule_filter_all),
                    selected = entry.selectedId.isBlank(),
                    onClick = {
                        entry.onSelect("")
                        picker = null
                    },
                )
            }
            entry.options.forEach { item ->
                BottomSheetItem(
                    icon = null,
                    title = item.name,
                    selected = item.id == entry.selectedId,
                    onClick = {
                        entry.onSelect(item.id)
                        picker = null
                    },
                )
            }
        }
    }
}

/**
 * 筛选区：一行可横滑的筛选胶囊（搜索已挪到标题栏，见 [JwxtScheduleTargetScreen]）。
 *
 * 教师页**没有职称筛选**：接口的 `jobTitle` 实测失效（连数据里存在的"教授"都筛不出来），
 * 做了只会让用户以为"这个职称没老师"，所以只做院系与性别。
 *
 * 「只看有课」三种类型都有：教室发 `arranged`（该教室是否已排课），教师 / 班级发 `teached`。
 */
@Composable
private fun TargetFilterBar(
    type: ScheduleTargetType,
    state: ScheduleQueryUiState,
    viewModel: JwxtScheduleQueryViewModel,
    onPick: (FilterEntry) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filterEntries(type, state, viewModel).forEach { entry ->
            SectionFilterChip(
                text = entry.label,
                selected = entry.selectedId.isNotBlank(),
                onClick = { onPick(entry) },
            )
        }
        SectionFilterChip(
            text = stringResource(R.string.jwxt_schedule_filter_teached_only),
            selected = state.onlyWithLessons,
            onClick = { viewModel.setOnlyWithLessons(!state.onlyWithLessons) },
        )
        // 只看外聘：教师页独有（接口参数 outSource）
        if (type == ScheduleTargetType.TEACHER) {
            SectionFilterChip(
                text = stringResource(R.string.jwxt_schedule_filter_outsource_only),
                selected = state.outSourceOnly,
                onClick = { viewModel.setOutSourceOnly(!state.outSourceOnly) },
            )
        }
    }
}

/**
 * 造一个筛选胶囊。
 *
 * 胶囊文案：**没选时显示"这个胶囊筛选的是什么"**（如「性别」），选了才显示选中的那一项（如「男」）。
 * 原来没选时一律显示「全部」，用户看不出每个胶囊是干什么用的。
 *
 * [allowAll] 为 false 表示这一项必选（目前只有学期）。7 个胶囊的字段结构完全一样，
 * 所以统一走这一个构造器——改样式时只改这里。
 */
@Composable
private fun dictEntry(
    @StringRes titleRes: Int,
    options: List<JwxtDictItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    allowAll: Boolean = true,
): FilterEntry {
    val title = stringResource(titleRes)
    return FilterEntry(
        label = options.firstOrNull { it.id == selectedId }?.name.orEmpty().ifBlank { title },
        title = title,
        options = options,
        selectedId = selectedId,
        onSelect = onSelect,
        allowAll = allowAll,
    )
}

/** 当前类型该显示哪些筛选胶囊（顺序即界面上从左到右的顺序） */
@Composable
private fun filterEntries(
    type: ScheduleTargetType,
    state: ScheduleQueryUiState,
    viewModel: JwxtScheduleQueryViewModel,
): List<FilterEntry> {
    val entries = mutableListOf(
        // 学期必选，所以不给「全部」
        dictEntry(
            titleRes = R.string.jwxt_schedule_filter_term,
            options = state.terms.map { JwxtDictItem(id = it.termCode, name = it.termName) },
            selectedId = state.termCode,
            onSelect = viewModel::setTerm,
            allowAll = false,
        ),
    )

    when (type) {
        ScheduleTargetType.CLASSROOM -> {
            entries += dictEntry(
                titleRes = R.string.jwxt_schedule_filter_campus,
                options = state.campusOptions,
                selectedId = state.campusCode,
                onSelect = viewModel::setCampus,
            )
            // 楼栋要先选校区才有（接口要求 campusNo）
            if (state.buildingOptions.isNotEmpty()) {
                entries += dictEntry(
                    titleRes = R.string.jwxt_schedule_filter_building,
                    options = state.buildingOptions,
                    selectedId = state.buildingCode,
                    onSelect = viewModel::setBuilding,
                )
            }
        }

        ScheduleTargetType.TEACHER -> {
            entries += dictEntry(
                titleRes = R.string.jwxt_schedule_filter_depart,
                options = state.departOptions,
                selectedId = state.departId,
                onSelect = viewModel::setDepart,
            )
            entries += dictEntry(
                titleRes = R.string.jwxt_schedule_filter_sex,
                options = state.sexOptions,
                selectedId = state.sexId,
                onSelect = viewModel::setSex,
            )
        }

        ScheduleTargetType.CLASS -> {
            entries += dictEntry(
                titleRes = R.string.jwxt_schedule_filter_grade,
                options = state.gradeOptions,
                selectedId = state.gradeId,
                onSelect = viewModel::setGrade,
            )
            entries += dictEntry(
                titleRes = R.string.jwxt_schedule_filter_college,
                options = state.collegeOptions,
                selectedId = state.collegeId,
                onSelect = viewModel::setCollege,
            )
            // 专业要先选年级 + 学院才有
            if (state.majorOptions.isNotEmpty()) {
                entries += dictEntry(
                    titleRes = R.string.jwxt_schedule_filter_major,
                    options = state.majorOptions,
                    selectedId = state.majorId,
                    onSelect = viewModel::setMajor,
                )
            }
        }
    }

    return entries
}

/** 结果列表：滚动接近底部自动加载下一页 */
@Composable
private fun ScheduleRowList(
    state: ScheduleQueryUiState,
    onLoadMore: () -> Unit,
    onRowClick: (ScheduleRow) -> Unit,
) {
    val listState = rememberLazyListState()
    val hasMore by rememberUpdatedState(state.hasMore)
    val isLoadingMore by rememberUpdatedState(state.isLoadingMore)

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 3 && hasMore && !isLoadingMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(state.rows) { index, row ->
            ScheduleRowItem(row = row, onClick = { onRowClick(row) })
            if (index < state.rows.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        }
        item(key = "footer") {
            PagingFooter(
                isLoadingMore = state.isLoadingMore,
                hasMore = state.hasMore,
                onLoadMore = onLoadMore,
            )
        }
    }
}

/** 一行结果：主名称 + 次信息 + 标签 */
@Composable
private fun ScheduleRowItem(row: ScheduleRow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.subtitle.isNotBlank() && row.subtitle != row.name) {
            Text(
                text = row.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.tags.isNotEmpty()) {
            Text(
                text = row.tags.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
