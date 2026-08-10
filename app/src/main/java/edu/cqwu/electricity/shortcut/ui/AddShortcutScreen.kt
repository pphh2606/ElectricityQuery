package edu.cqwu.electricity.shortcut.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import edu.cqwu.electricity.R
import edu.cqwu.electricity.home.data.HomeApp
import edu.cqwu.electricity.home.data.HomeCategory
import edu.cqwu.electricity.home.data.HomeJsonLoader
import edu.cqwu.electricity.theme.ui.BottomSheetDialog
import edu.cqwu.electricity.theme.ui.BottomSheetItem
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.LocalTopBarState
import edu.cqwu.electricity.theme.ui.toTopAppBarColors
import edu.cqwu.electricity.shortcut.util.ShortcutHelper
import edu.cqwu.electricity.theme.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 快捷方式名称最大长度 */
private const val MAX_SHORTCUT_NAME_LENGTH = 12

/**
 * 添加快捷方式页面
 *
 * 布局：预览（常驻顶部）→ 名称输入 → 选择功能按钮（弹出底部弹窗）→ 创建按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShortcutScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbarController.current
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf<List<HomeCategory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedApp by remember { mutableStateOf<HomeApp?>(null) }
    var shortcutName by remember { mutableStateOf("") }
    var showFunctionSheet by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }

    // 加载首页功能列表
    LaunchedEffect(Unit) {
        val loader = HomeJsonLoader(context)
        val result = withContext(Dispatchers.IO) { loader.loadCategories() }
        result.onSuccess { categories = it }
            .onFailure { e ->
                loadError = e.message ?: "加载失败"
                snackbar.show(resources.getString(R.string.common_load_failed), ToastUtils.Type.ERROR)
            }
        isLoading = false
    }

    // 选中功能时自动填充名称
    LaunchedEffect(selectedApp) {
        selectedApp?.let { shortcutName = it.name }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.shortcut_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                colors = topBarColors
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            loadError != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = loadError ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    // ── 预览区（常驻顶部） ──
                    ShortcutPreview(
                        appName = shortcutName.ifEmpty { selectedApp?.name ?: "" },
                        iconUrl = selectedApp?.iconUrl ?: ""
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── 选择功能按钮（样式统一：未选时显示 + 图标，选中后显示功能图标） ──
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFunctionSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedApp != null) {
                                AppIcon(
                                    iconUrl = selectedApp!!.iconUrl,
                                    fallbackText = selectedApp!!.name.firstOrNull()?.toString() ?: "?",
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = selectedApp?.name ?: stringResource(R.string.shortcut_select_function),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = if (selectedApp != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.shortcut_select_function),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // ── 名称输入（选择功能后才显示） ──
                    if (selectedApp != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = shortcutName,
                            onValueChange = { if (it.length <= MAX_SHORTCUT_NAME_LENGTH) shortcutName = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.shortcut_name_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // ── 创建按钮 ──
                    Button(
                        enabled = selectedApp != null && !isCreating,
                        onClick = {
                            val app = selectedApp
                            if (app == null) {
                                snackbar.show(
                                    resources.getString(R.string.shortcut_no_function_selected),
                                    ToastUtils.Type.ERROR
                                )
                                return@Button
                            }
                            isCreating = true
                            val label = shortcutName.ifEmpty { app.name }
                            val appInfo = ShortcutHelper.ShortcutAppInfo(
                                appId = app.appId,
                                appName = app.name,
                                openUrl = app.openUrl,
                                iconUrl = app.iconUrl
                            )
                            scope.launch {
                                when (val result = ShortcutHelper.createPinnedShortcut(context, appInfo, label)) {
                                    is ShortcutHelper.CreateResult.Success -> {
                                        snackbar.show(
                                            resources.getString(R.string.shortcut_success),
                                            ToastUtils.Type.SUCCESS
                                        )
                                        isCreating = false
                                        selectedApp = null
                                        shortcutName = ""
                                    }
                                    is ShortcutHelper.CreateResult.NotSupported -> {
                                        isCreating = false
                                        snackbar.show(
                                            resources.getString(R.string.shortcut_not_supported),
                                            ToastUtils.Type.ERROR
                                        )
                                    }
                                    is ShortcutHelper.CreateResult.Failed -> {
                                        isCreating = false
                                        snackbar.show(
                                            resources.getString(R.string.shortcut_permission_hint),
                                            ToastUtils.Type.ERROR
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.shortcut_create),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // ── 功能选择底部弹窗 ──
    BottomSheetDialog(
        visible = showFunctionSheet,
        onDismissRequest = { showFunctionSheet = false },
        title = stringResource(R.string.shortcut_select_function),
        fullscreen = false,
        skipPartiallyExpanded = false,
        leadingButton = {
            TextButton(onClick = { showFunctionSheet = false }) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                categories.forEach { category ->
                    item(key = "cat_${category.categoryId}") {
                        Text(
                            text = category.categoryName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(
                        items = category.apps,
                        key = { it.appId }
                    ) { app ->
                        BottomSheetItem(
                            icon = null,
                            title = app.name,
                            selected = selectedApp?.appId == app.appId,
                            iconUrl = app.iconUrl.ifBlank { null },
                            containerColor = if (selectedApp?.appId == app.appId) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            onClick = {
                                selectedApp = app
                                showFunctionSheet = false
                            }
                        )
                    }
                }
                // 底部安全间距
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
}

// ═══════════════════════════════════════════════
//  私有子组件
// ═══════════════════════════════════════════════

/**
 * 快捷方式桌面预览（常驻顶部）
 * 自动根据 appName 和 iconUrl 判断是否为占位状态。
 */
@Composable
private fun ShortcutPreview(
    appName: String,
    iconUrl: String,
) {
    val isPlaceholder = appName.isBlank() && iconUrl.isBlank()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isPlaceholder) {
                // 未选择功能时的占位（仅显示图标轮廓，下方保留空白占位防止移位）
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "?",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = " ") // 空白占位，保持布局稳定
        } else {
            // 已选中功能的预览
            AppIcon(
                iconUrl = iconUrl,
                fallbackText = appName.firstOrNull()?.toString() ?: "?",
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = appName.ifEmpty { "？" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 统一的应用图标组件，与首页 [AppIconBox] 风格一致：
 * surfaceVariant 圆角背景 + 内边距包裹图标。
 */
@Composable
private fun AppIcon(
    iconUrl: String,
    fallbackText: String,
    modifier: Modifier = Modifier,
    iconPadding: Dp = 4.dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (iconUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(iconUrl)
                    .size(128)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(iconPadding)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = fallbackText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
