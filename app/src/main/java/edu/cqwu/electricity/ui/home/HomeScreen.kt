package edu.cqwu.electricity.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import edu.cqwu.electricity.data.model.CustomServiceEntry
import edu.cqwu.electricity.data.model.HomeApp
import edu.cqwu.electricity.data.model.HomeAppIds
import edu.cqwu.electricity.ui.components.BottomSheetDialog
import edu.cqwu.electricity.ui.components.CustomWebsiteDialog
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import edu.cqwu.electricity.util.ToastUtils
import edu.cqwu.electricity.util.WebViewUrlUtil

/**
 * 首页 TopAppBar，由 [MainTabScreen] 在 Scaffold.topBar 中按页面切换调用。
 * 支持搜索模式和编辑模式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    onNavigateToScan: () -> Unit,
    searchQuery: String,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
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
                            text = "搜索应用...",
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
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "清除搜索",
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
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "退出搜索",
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
                    text = "首页",
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onNavigateToScan) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "扫码",
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
    onNavigateToBuildingSelection: () -> Unit,
    onNavigateToWebView: (url: String, title: String) -> Unit,
    onNavigateToQrCode: (edu.cqwu.electricity.data.network.QrCodeType) -> Unit,
    onNavigateToCardCenter: () -> Unit,
    onNavigateToNotice: () -> Unit,
    onNavigateToFeeServiceHall: () -> Unit = {},
    onNavigateToMyInfo: () -> Unit = {},
    homeViewModel: HomeViewModel = viewModel()
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current

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

    // 提取 handleAppClick 为局部函数，消除三处重复传参
    val handleAppClickInternal: (HomeApp) -> Unit = { app ->
        handleAppClick(
            app = app,
            onNavigateToBuildingSelection = onNavigateToBuildingSelection,
            onNavigateToWebView = onNavigateToWebView,
            onNavigateToQrCode = onNavigateToQrCode,
            onNavigateToCardCenter = onNavigateToCardCenter,
            onNavigateToNotice = onNavigateToNotice,
            onNavigateToFeeServiceHall = onNavigateToFeeServiceHall,
            onNavigateToMyInfo = onNavigateToMyInfo,
            onExternalIntent = { url, name -> pendingExternalIntent = name to url }
        )
    }

    // 执行外部 Intent 跳转
    fun openExternalIntent(appName: String, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "系统无法处理 $url (scheme=${Uri.parse(url).scheme}), 尝试降级...")
            // ── 降级方案 ──
            if (url.startsWith("mamp://")) {
                try {
                    Log.d(TAG, "降级: 尝试 campusnextins:// 打开今日校园App")
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("campusnextins://"))
                    context.startActivity(fallbackIntent)
                    return
                } catch (e2: ActivityNotFoundException) {
                    Log.w(TAG, "降级 campusnextins:// 也失败: ${e2.message}")
                }
            }
            snackbar.show("请安装今日校园App以使用「$appName」", ToastUtils.Type.ERROR)
        } catch (e: Exception) {
            Log.e(TAG, "打开外部应用异常: ${e.message}")
            snackbar.show("打开失败: ${e.message}", ToastUtils.Type.ERROR)
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { homeViewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: "加载失败",
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
                            text = "未找到匹配的应用",
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
                                text = "搜索结果（共 ${filteredApps.size} 个）",
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
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
                                onNavigateToWebView(entry.url, entry.title)
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

    // 外部 Intent 确认底部弹窗
    pendingExternalIntent?.let { (appName, url) ->
        BottomSheetDialog(
            onDismissRequest = { pendingExternalIntent = null },
            title = "打开外部应用",
            icon = Icons.Default.OpenInBrowser,
            leadingButton = {
                TextButton(onClick = { pendingExternalIntent = null }) {
                    Text("取消")
                }
            },
            trailingButton = {
                TextButton(onClick = {
                    pendingExternalIntent = null
                    openExternalIntent(appName, url)
                }) {
                    Text("确认")
                }
            }
        ) {
            Text(
                text = "「$appName」需要在今日校园内打开，确认继续？",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // ── 自定义网站弹窗 ──
    if (showCustomWebsiteDialog) {
        CustomWebsiteDialog(
            onDismiss = { showCustomWebsiteDialog = false },
            onConfirm = { title, url, iconUri ->
                showCustomWebsiteDialog = false
                homeViewModel.addCustomService(title, url, iconUri)
            }
        )
    }

}

/**
 * 处理应用点击事件
 * - "学生宿舍电费充值" (appId: 7624123418505155) → 打开原生电费查询
 * - http/https URL → 在统一内置浏览器中打开
 * - 自定义 scheme (mamp:// 等) → 弹出确认弹窗，确认后通过 Intent 在外部打开
 */
private const val TAG = "HomeScreen"

private fun handleAppClick(
    app: HomeApp,
    onNavigateToBuildingSelection: () -> Unit,
    onNavigateToWebView: (url: String, title: String) -> Unit,
    onNavigateToQrCode: (type: edu.cqwu.electricity.data.network.QrCodeType) -> Unit,
    onNavigateToCardCenter: () -> Unit,
    onNavigateToNotice: () -> Unit,
    onNavigateToFeeServiceHall: () -> Unit = {},
    onNavigateToMyInfo: () -> Unit = {},
    onExternalIntent: (url: String, appName: String) -> Unit
) {
    // 支付码 → 原生二维码显示
    if (app.appId == HomeAppIds.PAY_QR) {
        onNavigateToQrCode(edu.cqwu.electricity.data.network.QrCodeType.PAY)
        return
    }
    // 乘车码 → 原生二维码显示
    if (app.appId == HomeAppIds.BUS_QR) {
        onNavigateToQrCode(edu.cqwu.electricity.data.network.QrCodeType.BUS)
        return
    }
    // 学生宿舍电费充值 → 打开原生电费查询
    if (app.appId == HomeAppIds.DORM_ELECTRICITY) {
        onNavigateToBuildingSelection()
        return
    }
    // 卡中心 → 原生卡中心页面
    if (app.appId == HomeAppIds.CARD_CENTER) {
        onNavigateToCardCenter()
        return
    }
    // 通知公告 → 原生通知公告列表页
    if (app.appId == HomeAppIds.NOTICE) {
        onNavigateToNotice()
        return
    }
    // 缴费服务大厅 → 打开原生页面
    if (app.appId == HomeAppIds.FEE_SERVICE_HALL) {
        onNavigateToFeeServiceHall()
        return
    }
    // 我的信息 → 打开原生页面
    if (app.appId == HomeAppIds.MY_INFO) {
        onNavigateToMyInfo()
        return
    }

    val url = app.openUrl

    // 非 http/https 协议 → 弹出确认弹窗
    if (!WebViewUrlUtil.isHttpScheme(url)) {
        onExternalIntent(url, app.name)
        return
    }

    // http/https → 统一内置浏览器
    onNavigateToWebView(url, app.name)
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // ── 标题行：「我的服务」+ 编辑/确认图标 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "我的服务",
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
                    imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (isEditMode) "确认并退出编辑" else "编辑我的服务",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── 横向滚动行：收藏的服务 + 自定义服务 + 添加按钮 ──
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
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "移除",
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
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "移除",
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── 固定在末尾的「+ 自定义网站」按钮 ──
            item(key = "add_custom_service") {
                AddCustomServiceButton(onClick = onAddCustomService)
            }
        }

        // 分割线
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )
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
                imageVector = Icons.Default.Language,
                contentDescription = "自定义网站",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = "自定义网站",
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
                    onError = { }
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
                    imageVector = Icons.Default.Language,
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
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加到我的服务",
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
        null
    }
}
