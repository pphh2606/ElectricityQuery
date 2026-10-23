@file:OptIn(ExperimentalMaterial3Api::class)

package edu.cqwu.electricity.home.ui

import edu.cqwu.electricity.common.ui.BlurPullRefreshIndicator
import edu.cqwu.electricity.common.ui.SectionFilterChip
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.theme.ui.currentTopBarColors

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import edu.cqwu.electricity.R

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.chrisbanes.haze.HazeState
import edu.cqwu.electricity.common.settings.LocalAppSettingsState
import edu.cqwu.electricity.common.ui.AppIconBox
import edu.cqwu.electricity.common.ui.ConfirmBottomSheet
import edu.cqwu.electricity.common.ui.FeatureGrid
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.common.ui.isHeaderBlurOn
import edu.cqwu.electricity.common.ui.rememberHazeBlurModifier
import edu.cqwu.electricity.common.ui.rememberHazeSourceModifier
import edu.cqwu.electricity.common.settings.CustomServiceEntry
import edu.cqwu.electricity.home.data.ExternalAppOpener
import edu.cqwu.electricity.home.data.HomeApp
import edu.cqwu.electricity.home.data.HomeAppLauncher
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.common.navigation.LocalNavController
import edu.cqwu.electricity.common.util.ToastUtils
import kotlinx.coroutines.launch

/**
 * 首页 TopAppBar，由 [MainTabScreen] 在 Scaffold.topBar 中按页面切换调用。
 * 支持搜索模式和编辑模式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    searchQuery: String,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    containerColor: Color? = null,
) {
    val nav = LocalNavController.current
    val baseColors = currentTopBarColors()
    // 毛玻璃模式下标题栏底色必须透明，否则会盖住 Haze 模糊层
    val topBarColors = if (containerColor != null) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
            navigationIconContentColor = baseColors.navigationIconContentColor,
            titleContentColor = baseColors.titleContentColor,
            actionIconContentColor = baseColors.actionIconContentColor,
        )
    } else {
        baseColors
    }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // 进入搜索模式时自动聚焦输入框
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    // 搜索模式下拦截系统返回键，退出搜索而非退出应用
    BackHandler(enabled = isSearching) {
        focusManager.clearFocus()
        onCloseSearch()
    }

    if (isSearching) {
        // ── 搜索模式 ──
        TopAppBar(
            title = {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.home_search_apps),
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
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Outlined.Clear,
                                    contentDescription = stringResource(R.string.common_clear_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    focusManager.clearFocus()
                    onCloseSearch()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.common_exit_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = topBarColors
        )
    } else {
        // ── 普通模式 ──
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.home_title),
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.common_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { nav.navigate(Routes.SCAN) }) {
                    Icon(
                        imageVector = Icons.Outlined.CenterFocusWeak,
                        contentDescription = stringResource(R.string.common_scan),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = topBarColors
        )
    }
}

/**
 * 首页整页容器：内容铺满并作为 Haze 模糊源，标题栏 + 分类索引条作为顶部浮层做毛玻璃。
 *
 * 刻意用 Box 叠层而不是 Column：标题栏必须与内容重叠，内容才能滚到标题栏背后，
 * Haze 才有可模糊的源。顶部留白 [contentTopInset] 由内部 LazyColumn 的第一个 item
 * 承担，滚动时该 item 会被推出标题栏区域，内容自然进入背后。
 */
@Composable
fun HomePage(
    homeViewModel: HomeViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }
    val headerBlurOn = isHeaderBlurOn()

    // 浮层实测高度（状态栏 + 标题栏 + 分类条），随搜索模式、分类条显隐自动修正，
    // 避免把高度写死导致标题栏与内容错位
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val contentTopInset: Dp = with(LocalDensity.current) { headerHeightPx.toDp() }
    // 分类索引点击后要把目标分组对齐到「标题栏下沿」，而不是屏幕顶端：
    // 列表从屏幕顶端铺满（模糊改造后），对齐屏幕顶端会让分组标题藏进标题栏浮层背后
    val contentTopInsetPx = with(LocalDensity.current) { contentTopInset.roundToPx() }

    // 标题栏模糊层：参数统一走 common/ui 的实现（与其它页共用同一套「高级模糊设置」参数）
    val headerModifier = rememberHazeBlurModifier(hazeState)

    Box(modifier = modifier.fillMaxSize()) {
        // ① 内容层：作为模糊源（标题栏是兄弟节点，2250 结构下模糊本身是生效的）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(rememberHazeSourceModifier(hazeState))
        ) {
            HomePageContent(
                homeViewModel = homeViewModel,
                listState = listState,
                contentTopInset = contentTopInset,
            )
        }

        // ② 顶部浮层：标题栏 + 分类条，压在内容之上并模糊其背后的内容
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { headerHeightPx = it.height }
                .then(headerModifier)
                // 标题栏空白区域的点击不要穿透到底下的列表
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            HomeTopBar(
                searchQuery = uiState.searchQuery,
                isSearching = uiState.isSearching,
                onSearchQueryChange = { homeViewModel.setSearchQuery(it) },
                onToggleSearch = { homeViewModel.enterSearchMode() },
                onCloseSearch = { homeViewModel.clearSearch() },
                containerColor = if (headerBlurOn) Color.Transparent else null,
            )

            // 分类索引条不是启动第一时间就有（要等首页数据回来），
            // 用 AnimatedVisibility 淡入展开，避免它突然出现把标题栏和列表内容同时顶一下
            AnimatedVisibility(
                visible = !uiState.isLoading && uiState.error == null && !uiState.isSearching,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
            ) {
                // LazyColumn 里 item 0 是顶部留白，item 1 才是「我的服务」，分类从 2 开始。
                // 取「标题栏下沿以下第一个可见项」而不是 firstVisibleItemIndex：
                // 胶囊跳转会让上一项尾部留在标题栏背后，用 firstVisibleItemIndex 会高亮错一格
                val activeIndex = (
                    (listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.offset + it.size > contentTopInsetPx }?.index ?: 0) - 1
                    ).coerceIn(0, uiState.categories.size)
                // 索引条下方的细线只要列表「已经滚下去」就出现：
                // 不能用 firstVisibleItemIndex 判断——列表首项是顶栏留白占位，
                // 那样要滚过整个留白高度（浮层高）才会触发，看起来像"滑一段距离才出来"
                val showDivider = listState.canScrollBackward
                HomeSectionIndex(
                    categoryNames = uiState.categories.map { it.categoryName },
                    activeIndex = activeIndex,
                    showDivider = showDivider,
                    onSectionClick = { index ->
                        // 负偏移 = 让目标分组停在标题栏下沿，正好露出分组标题
                        scope.launch {
                            listState.animateScrollToItem(index + 1, -contentTopInsetPx)
                        }
                    },
                )
            }
        }
    }
}

/**
 * 首页页面内容（不含 TopAppBar / BottomBar），
 * 由 [HomePage] 作为 Haze 模糊源包住调用。
 * 支持搜索模式：搜索时显示过滤结果网格，无搜索时显示原始分类列表。
 * 支持编辑模式：在分类列表上方显示「我的服务」区域。
 *
 * @param listState 由 [HomePage] 持有，供顶部浮层的分类索引条联动
 * @param contentTopInset 顶部浮层高度，作为列表第一个 item 的留白（必须随列表滚走，不能写进 contentPadding）
 */
@Composable
fun HomePageContent(
    homeViewModel: HomeViewModel = viewModel(),
    listState: LazyListState = rememberLazyListState(),
    contentTopInset: Dp = 0.dp,
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 切换语言只重建 Activity，ViewModel 会被保留、init 不再执行；
    // 这里用配置变化当信号，让 ViewModel 按新语言静默重载首页数据（不显示下拉刷新指示器）
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration) {
        homeViewModel.reloadForLanguageChange()
    }

    val snackbar = LocalSnackbarController.current
    val nav = LocalNavController.current

    // 外部 Intent 确认弹窗状态：（appName, url）
    var pendingExternalIntent by remember { mutableStateOf<Pair<String, String>?>(null) }

    // 搜索过滤结果
    val isSearching = uiState.isSearching
    val filteredApps by remember(uiState.categories, uiState.searchQuery) {
        derivedStateOf {
            val query = uiState.searchQuery.trim()
            if (query.isEmpty()) {
                emptyList()
            } else {
                uiState.categories
                    .flatMap { it.apps }
                    .filter { app ->
                        // 同时匹配译文、JSON 中文原文与服务端别名：英文界面下搜中文关键词同样能命中
                        app.name.contains(query, ignoreCase = true) ||
                                (app.originalName?.contains(query, ignoreCase = true) == true) ||
                                (app.aliasName?.contains(query, ignoreCase = true) == true)
                    }
            }
        }
    }
    // 搜索结果按行分块（每行4个），用于 LazyColumn 逐行懒加载
    val searchRows by remember(filteredApps) {
        derivedStateOf { filteredApps.chunked(4) }
    }

    // 我的服务应用列表
    val myServiceApps by remember(uiState.categories, uiState.myServiceIds) {
        derivedStateOf { homeViewModel.myServiceApps }
    }

    // 自定义网站对话框状态
    var showCustomWebsiteDialog by remember { mutableStateOf(false) }

    // 统一应用点击分发（原生 / 内置浏览器 / 外部弹窗），三处入口共用
    val handleAppClickInternal: (HomeApp) -> Unit = { app ->
        HomeAppLauncher.launch(
            appId = app.appId,
            name = app.name,
            openUrl = app.openUrl,
            navigate = { nav.navigate(it) },
            onExternal = { url, name -> pendingExternalIntent = name to url },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            // 首次加载与手动下拉共用同一刷新指示器（不再显示页面中央转圈）
            isRefreshing = uiState.isRefreshing || uiState.isLoading,
            onRefresh = { homeViewModel.refresh() },
            state = pullState,
            indicator = { BlurPullRefreshIndicator(pullState, uiState.isRefreshing || uiState.isLoading, contentTopInset) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // 首载失败（还没有任何内容）：整页错误态 + 重试；已加载后的下拉刷新失败只弹 toast，
            // 不再把整个首页内容顶掉（HomeViewModel.refresh 失败时会保留 categories）。
            LaunchedEffect(uiState.error, uiState.categories.isEmpty()) {
                val message = uiState.error
                if (message != null && uiState.categories.isNotEmpty()) {
                    snackbar.show(message, ToastUtils.Type.ERROR)
                }
            }

            when {
                uiState.error != null && uiState.categories.isEmpty() -> {
                    ReLoginContent(
                        errorMessage = uiState.error,
                        requiresReLogin = false,
                        onReLogin = {},
                        onRetry = { homeViewModel.refresh() },
                    )
                }
                isSearching -> {
                    // ── 搜索模式：平铺搜索结果（懒加载） ──
                    if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.home_no_match),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            item(key = "top_inset") { Spacer(Modifier.height(contentTopInset)) }
                            // 搜索结果标题
                            item(key = "search_header") {
                                Text(
            text = pluralStringResource(R.plurals.home_search_result, filteredApps.size, filteredApps.size),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            // ✅ 每行作为独立 LazyColumn item，支持真正懒加载
                            items(
                                count = searchRows.size,
                                key = { index -> searchRows[index].joinToString("-") { it.appId } }
                            ) { index ->
                                val rowApps = searchRows[index]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    rowApps.forEach { app ->
                                        AppIconItem(
                                            app = app,
                                            modifier = Modifier.weight(1f),
                                            onClick = { handleAppClickInternal(app) },
                                            showAddBadge = uiState.isEditMode,
                                            isMyService = app.appId in uiState.myServiceIds,
                                            onAddClick = {
                                                homeViewModel.addToMyServices(app.appId)
                                            }
                                        )
                                    }
                                    // 补齐空位
                                    repeat(4 - rowApps.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    // ── 普通模式：按分类展示，顶部插入「我的服务」区域 ──
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        item(key = "top_inset") { Spacer(Modifier.height(contentTopInset)) }
                        // ── 我的服务区域（始终在分类列表顶部） ──
                        item(key = "my_services") {
                            MyServicesSection(
                                myServiceApps = myServiceApps,
                                customServices = uiState.customServices,
                                isEditMode = uiState.isEditMode,
                                onToggleEditMode = {
                                    if (uiState.isEditMode) {
                                        homeViewModel.exitEditMode()
                                    } else {
                                        homeViewModel.enterEditMode()
                                    }
                                },
                                onRemoveService = { app ->
                                    homeViewModel.removeFromMyServices(app.appId)
                                },
                                onRemoveCustomService = { id ->
                                    homeViewModel.removeCustomService(id)
                                },
                                onServiceClick = { app -> handleAppClickInternal(app) },
                                onCustomServiceClick = { entry ->
                                    nav.navigate(Routes.unifiedWebViewRoute(entry.url, entry.title))
                                },
                                onAddCustomService = {
                                    showCustomWebsiteDialog = true
                                }
                            )
                        }

                        items(
                            items = uiState.categories,
                            key = { it.categoryId }
                        ) { category ->
                            CategorySection(
                                categoryName = category.categoryName,
                                apps = category.apps,
                                isEditMode = uiState.isEditMode,
                                myServiceIds = uiState.myServiceIds,
                                onAddToService = { appId ->
                                    homeViewModel.addToMyServices(appId)
                                },
                                onAppClick = { app -> handleAppClickInternal(app) }
                            )
                        }
                    }
                }
            }
        }
    }

    // 外部 Intent 确认底部弹窗（与桌面快捷方式共用同一套确认组件）
    ConfirmBottomSheet(
        visible = pendingExternalIntent != null,
        onDismissRequest = { pendingExternalIntent = null },
        title = stringResource(R.string.home_external_app_title),
        message = pendingExternalIntent?.let { stringResource(R.string.home_external_app_message, it.first) },
        icon = Icons.Outlined.OpenInBrowser,
        confirmText = stringResource(R.string.common_confirm),
        onConfirm = {
            pendingExternalIntent?.let { (name, url) ->
                pendingExternalIntent = null
                ExternalAppOpener.open(context, name, url) { message ->
                    snackbar.show(message, ToastUtils.Type.ERROR)
                }
            }
        },
    )

    // ── 自定义网站弹窗 ──
    CustomWebsiteDialog(
        visible = showCustomWebsiteDialog,
        onDismiss = { showCustomWebsiteDialog = false },
        onConfirm = { title, url, iconUri ->
            showCustomWebsiteDialog = false
            homeViewModel.addCustomService(title, url, iconUri)
        }
    )

}

/**
 * 首页分区索引栏：固定显示在 TopAppBar 下方。
 * 索引项顺序与 LazyColumn item 顺序一致，点击后平滑滚动到对应区块。
 */
@Composable
private fun HomeSectionIndex(
    categoryNames: List<String>,
    activeIndex: Int,
    showDivider: Boolean,
    onSectionClick: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "my_services") {
                SectionFilterChip(
                    text = stringResource(R.string.home_my_services),
                    selected = activeIndex == 0,
                    onClick = { onSectionClick(0) },
                )
            }
            items(
                count = categoryNames.size,
                key = { index -> "category_$index" },
            ) { index ->
                SectionFilterChip(
                    text = categoryNames[index],
                    selected = activeIndex == index + 1,
                    onClick = { onSectionClick(index + 1) },
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
            )
        }
    }
}

/**
 * 「我的服务」区域：标题 + 编辑按钮 + 横向可滚动图标行。
 * 显示在首页分类列表的最顶部。
 */
@Composable
private fun MyServicesSection(
    myServiceApps: List<HomeApp>,
    customServices: List<CustomServiceEntry>,
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit,
    onRemoveService: (HomeApp) -> Unit,
    onRemoveCustomService: (String) -> Unit,
    onServiceClick: (HomeApp) -> Unit,
    onCustomServiceClick: (CustomServiceEntry) -> Unit,
    onAddCustomService: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // ── 标题行：「我的服务」+ 编辑/确认图标 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_my_services),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onToggleEditMode,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isEditMode) Icons.Outlined.Check else Icons.Outlined.Edit,
                    contentDescription = if (isEditMode) stringResource(R.string.home_confirm_exit_edit) else stringResource(R.string.home_edit_my_services),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── 横向滚动行：编辑模式或有内容时才显示，显示/隐藏切换带动画 ──
        AnimatedVisibility(visible = isEditMode || myServiceApps.isNotEmpty() || customServices.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
            // 收藏的应用图标
            items(
                items = myServiceApps,
                key = { it.appId }
            ) { app ->
                Box {
                    MyServiceIconItem(
                        app = app,
                        onClick = { onServiceClick(app) }
                    )
                    if (isEditMode) {
                        IconButton(
                            onClick = { onRemoveService(app) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.common_remove),
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 自定义网站快捷方式
            items(
                items = customServices,
                key = { it.id }
            ) { entry ->
                Box {
                    CustomServiceIconItem(
                        entry = entry,
                        onClick = { onCustomServiceClick(entry) }
                    )
                    if (isEditMode) {
                        IconButton(
                            onClick = { onRemoveCustomService(entry.id) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.common_remove),
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── 编辑模式下才显示「+ 自定义网站」按钮 ──
            item(key = "add_custom_service") {
                if (isEditMode) {
                    AddCustomServiceButton(onClick = onAddCustomService)
                } else if (myServiceApps.isEmpty() && customServices.isEmpty()) {
                    // 无服务退出编辑时保持等高占位，避免高度塌缩导致收起动画不可见
                    Box(modifier = Modifier.size(60.dp, 80.dp))
                }
            }
            }
        }
    }
}

/**
 * 固定在最右边的「+ 自定义网站」按钮。
 */
@Composable
private fun AddCustomServiceButton(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = stringResource(R.string.custom_website_title),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = stringResource(R.string.home_custom_website),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(52.dp)
                .padding(top = 4.dp)
        )
    }
}

/**
 * 自定义网站的单个图标项。
 *
 * 图标优先级：
 * 1. 用户自定义图片（entry.iconUri）
 * 2. 网站 favicon.ico
 * 3. 默认 Language 图标
 */
@Composable
private fun CustomServiceIconItem(
    entry: CustomServiceEntry,
    onClick: () -> Unit
) {
    var useFallbackIcon by remember(entry.id) { mutableStateOf(false) }
    // 自定义图片 → 加载失败或没有时尝试 favicon → 都失败时用默认图标
    val iconUrl = if (!useFallbackIcon) {
        entry.iconUri ?: getFaviconUrl(entry.url)
    } else null

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (iconUrl != null) {
            // 尝试加载自定义图片或 favicon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(iconUrl)
                        .size(128)
                        .crossfade(true)
                        .build(),
                    contentDescription = entry.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                    onError = { useFallbackIcon = true }
                )
            }
        } else {
            // 兜底：显示默认图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Language,
                    contentDescription = entry.title,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(52.dp)
                .padding(top = 4.dp)
        )
    }
}

/**
 * 我的服务中的单个图标项（与首页图标风格一致，但更紧凑）。
 */
@Composable
private fun MyServiceIconItem(
    app: HomeApp,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconBox(
            iconUrl = app.iconUrl,
            size = 40.dp,
            padding = 6.dp,
            contentDescription = app.name
        )

        Text(
            text = app.name,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(52.dp)
                .padding(top = 4.dp)
        )
    }
}

/**
 * 分类区块：标题 + 4 列图标网格。
 *
 * 网格交给公共组件 [FeatureGrid]（内部就是 `chunked` + `Row.weight(1f)` + 末行补空位，
 * 刻意不用 `LazyVerticalGrid`——嵌在 `LazyColumn` 里会因无限高度约束崩溃）。
 */
@Composable
private fun CategorySection(
    categoryName: String,
    apps: List<HomeApp>,
    isEditMode: Boolean = false,
    myServiceIds: Set<String> = emptySet(),
    onAddToService: (String) -> Unit = {},
    onAppClick: (HomeApp) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 分类标题
        Text(
            text = categoryName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        FeatureGrid(
            items = apps,
            columns = 4,
            rowSpacing = 0.dp,      // 首页行与行之间不留间距（与改造前一致）
            columnSpacing = 4.dp,
            modifier = Modifier.padding(top = 8.dp),
        ) { app, itemModifier ->
            AppIconItem(
                app = app,
                modifier = itemModifier,
                onClick = { onAppClick(app) },
                showAddBadge = isEditMode,
                isMyService = app.appId in myServiceIds,
                onAddClick = { onAddToService(app.appId) }
            )
        }
    }
}

/**
 * 单个应用图标项。
 *
 * @param showAddBadge 是否在右上角显示「+」添加按钮（编辑模式）
 * @param isMyService 该应用是否已在我的服务中（已收藏的不显示「+」号）
 * @param onAddClick 点击「+」号的回调
 */
@Composable
private fun AppIconItem(
    app: HomeApp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    showAddBadge: Boolean = false,
    isMyService: Boolean = false,
    onAddClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            // 刻意不 clip：角标要骑在图标右上角、会探出 Column 边界 3dp，一旦 clip 就会被圆角裁掉。
            // 本格子没有背景色，去掉 clip 只影响点击涟漪的形状（圆角 → 直角）
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 图标与它的「+」角标放进同一个窄 Box：角标对齐的是图标（44dp），不是整格，
            // 否则横屏时列变宽，角标会被推到离图标很远的格子右边缘
            Box {
                AppIconBox(
                    iconUrl = app.iconUrl,
                    size = 44.dp,
                    padding = 8.dp,
                    contentDescription = app.name
                )

                // 编辑模式下，未收藏的应用显示「+」号
                if (showAddBadge && !isMyService && onAddClick != null) {
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            // 沿 45° 向外挪半个角标（22dp 的一半），让圆心落在图标框右上角顶点上——
                            // 即"骑"在角上、一半露在框外
                            .offset(x = 11.dp, y = -11.dp)
                            .size(22.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.common_add_to_my_services),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = app.name,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                // 两行：长服务名（如"个人学业监测报告"）在单行下只能看到前几个字
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .padding(top = 4.dp)
            )
        }
    }
}

/**
 * 从网站 URL 中提取 favicon.ico 的地址。
 *
 * 例如:
 *   "http://218.194.176.214:8382/epay/" → "http://218.194.176.214:8382/favicon.ico"
 *   "https://www.example.com/page"     → "https://www.example.com/favicon.ico"
 */
private fun getFaviconUrl(url: String): String? {
    return try {
        val uri = Uri.parse(url)
        val host = uri.host ?: return null
        val port = uri.port
        val scheme = uri.scheme ?: "https"
        if (port != -1) "${scheme}://${host}:${port}/favicon.ico"
        else "${scheme}://${host}/favicon.ico"
    } catch (e: Exception) {
        AppLog.w("HomeScreen", "生成 favicon 地址失败: ${e.message}")
        null
    }
}
