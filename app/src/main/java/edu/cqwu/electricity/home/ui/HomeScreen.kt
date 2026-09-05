@file:OptIn(ExperimentalMaterial3Api::class)

package edu.cqwu.electricity.home.ui

import edu.cqwu.electricity.common.ui.SectionFilterChip
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.theme.ui.currentTopBarColors

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import edu.cqwu.electricity.R

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
import edu.cqwu.electricity.home.data.CustomServiceEntry
import edu.cqwu.electricity.home.data.ExternalAppOpener
import edu.cqwu.electricity.home.data.HomeApp
import edu.cqwu.electricity.home.data.HomeAppLauncher
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.theme.ui.LocalNavController
import edu.cqwu.electricity.theme.util.ToastUtils
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
) {
    val nav = LocalNavController.current
    val topBarColors = currentTopBarColors()
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
 * 首页页面内容（不含 Scaffold / TopAppBar / BottomBar），
 * 由 [MainTabScreen] 的 HorizontalPager 在 page 0 中调用。
 * 支持搜索模式：搜索时显示过滤结果网格，无搜索时显示原始分类列表。
 * 支持编辑模式：在分类列表上方显示「我的服务」区域。
 */
@Composable
fun HomePageContent(
    homeViewModel: HomeViewModel = viewModel()
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbarController.current
    val nav = LocalNavController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // LazyColumn item 顺序与索引一致：0 = 我的服务，1..n = 分类
    val activeSectionIndex by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex.coerceIn(0, uiState.categories.size)
        }
    }
    val showIndexDivider by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

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
                        app.name.contains(query, ignoreCase = true) ||
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
        if (!uiState.isLoading && uiState.error == null && !uiState.isSearching) {
            HomeSectionIndex(
                categoryNames = uiState.categories.map { it.categoryName },
                activeIndex = activeSectionIndex,
                showDivider = showIndexDivider,
                onSectionClick = { index ->
                    scope.launch {
                        listState.animateScrollToItem(index)
                    }
                },
            )
        }

        PullToRefreshBox(
            // 首次加载与手动下拉共用同一刷新指示器（不再显示页面中央转圈）
            isRefreshing = uiState.isRefreshing || uiState.isLoading,
            onRefresh = { homeViewModel.refresh() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                uiState.error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.error ?: stringResource(R.string.home_load_failed),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
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

    // 外部 Intent 确认底部弹窗（与桌面快捷方式共用 ExternalAppConfirmDialog）
    ExternalAppConfirmDialog(
        pending = pendingExternalIntent,
        onDismiss = { pendingExternalIntent = null },
        onConfirm = { name, url ->
            pendingExternalIntent = null
            ExternalAppOpener.open(context, name, url) { message ->
                snackbar.show(message, ToastUtils.Type.ERROR)
            }
        }
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
            .clip(MaterialTheme.shapes.small)
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
            .clip(MaterialTheme.shapes.small)
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
            .clip(MaterialTheme.shapes.small)
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
 * 通用的应用图标方框，带圆角背景和 AsyncImage 加载。
 * 被 [AppIconItem] 和 [MyServiceIconItem] 复用。
 */
@Composable
private fun AppIconBox(
    iconUrl: String,
    size: Dp,
    padding: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val context = LocalContext.current
        val imageRequest = remember(iconUrl) {
            ImageRequest.Builder(context)
                .data(iconUrl)
                .size(128)
                .crossfade(true)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * 分类区块：标题 + 4列图标网格
 * 使用 chunked(4) + Row.weight(1f) 避免 LazyVerticalGrid 嵌套 LazyColumn 的无限高度约束崩溃
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

        // 手动分 4 列网格，避免使用 LazyVerticalGrid
        Column(modifier = Modifier.padding(top = 8.dp)) {
            apps.chunked(4).forEach { rowApps ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowApps.forEach { app ->
                        AppIconItem(
                            app = app,
                            modifier = Modifier.weight(1f),
                            onClick = { onAppClick(app) },
                            showAddBadge = isEditMode,
                            isMyService = app.appId in myServiceIds,
                            onAddClick = { onAddToService(app.appId) }
                        )
                    }
                    // 补齐空位，使最后一行不足 4 个时保持布局一致
                    repeat(4 - rowApps.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
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
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIconBox(
                iconUrl = app.iconUrl,
                size = 44.dp,
                padding = 8.dp,
                contentDescription = app.name
            )

            Text(
                text = app.name,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .padding(top = 4.dp)
            )
        }

        // 编辑模式下，未收藏的应用显示「+」号
        if (showAddBadge && !isMyService && onAddClick != null) {
            IconButton(
                onClick = onAddClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
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
