package edu.cqwu.electricity.theme.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 通用底部弹窗，封装 MD3 ModalBottomSheet 的样板代码。
 *
 * 通过 [visible] 参数控制显隐，退出时自动播放动画，类似原生 Dialog.dismiss()。
 *
 * @param visible 控制弹窗是否可见。当从 `true` 变为 `false` 时，会先播放退出动画，
 *                动画完成后再移除内部 ModalBottomSheet。默认为 `true`（始终渲染）。
 *                设为 `false` 时仅在退出动画完成后才真正移除，不会立即消失。
 * @param onDismissRequest 用户请求关闭弹窗时触发的回调，通常在此将状态变量设为 false。
 *                         组件不会在内部隐藏动画结束后再次补调该回调。
 * @param fullscreen 如果为 true，内容区将撑满全屏高度并支持滚动。
 * @param leadingButton 拖拽手柄栏左侧的可选按钮（文字 / 图标），靠左边缘对齐。
 * @param trailingButton 拖拽手柄栏右侧的可选按钮（文字 / 图标），靠右边缘对齐。
 * @param contentModifier 应用到内容列的修饰符，位于默认布局之后。
 * @param contentPadding 内容列的内边距。
 * @param contentArrangement 内容项的垂直排列方式。
 * @param onHideStarted 隐藏动画开始前调用。
 *
 * 使用示例（推荐，visible 模式，选项点击也带退出动画）：
 * ```kotlin
 * BottomSheetDialog(
 *     visible = showSheet,
 *     onDismissRequest = { showSheet = false },
 *     title = "选择选项"
 * ) {
 *     BottomSheetItem(onClick = {
 *         doSomething()
 *         showSheet = false  // 自动播放退出动画
 *     })
 * }
 * ```
 *
 * 带手柄按钮的示例：
 * ```kotlin
 * BottomSheetDialog(
 *     visible = showSheet,
 *     onDismissRequest = { showSheet = false },
 *     title = "选择操作",
 *     leadingButton = {
 *         TextButton(onClick = { showSheet = false }) { Text("取消") }
 *     },
 *     trailingButton = {
 *         IconButton(onClick = { /* 设置 */ }) {
 *             Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.common_settings))
 *         }
 *     }
 * ) {
 *     BottomSheetItem(/* ... */)
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetDialog(
    visible: Boolean = true,
    onDismissRequest: () -> Unit,
    title: String? = null,
    icon: ImageVector? = null,
    fullscreen: Boolean = true,
    sheetGesturesEnabled: Boolean = true,
    /** 显式覆盖 skipPartiallyExpanded，默认为 `!fullscreen`。 */
    skipPartiallyExpanded: Boolean? = null,
    leadingButton: @Composable (() -> Unit)? = null,
    trailingButton: @Composable (() -> Unit)? = null,
    contentModifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    contentArrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp),
    fixedHeader: Boolean = false,
    bottomBar: @Composable (() -> Unit)? = null,
    onHideStarted: () -> Unit = {},
    content: @Composable () -> Unit
) {
    // 兼容 Android 7+ 的 sheet 状态行为。
    val computedSkip = skipPartiallyExpanded ?: !fullscreen
    val appDensity = LocalDensity.current
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = computedSkip
    )
    val isHiding = rememberBottomSheetHidingState(
        visible = visible,
        sheetState = sheetState,
        onHideStarted = onHideStarted,
    )

    // visible 或 isHiding 任一为 true 时渲染 ModalBottomSheet。
    if (visible || isHiding) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = if (fullscreen) Modifier.statusBarsPadding() else Modifier,
            sheetState = sheetState,
            sheetGesturesEnabled = sheetGesturesEnabled,
            dragHandle = {
                ProvideAppScaledDensity(appDensity) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧按钮，weight 等分空间，确保手柄始终居中。
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            leadingButton?.invoke()
                        }

                        // 居中拖拽手柄，固定宽度并自然居中。
                        Box(contentAlignment = Alignment.Center) {
                            BottomSheetDefaults.DragHandle()
                        }

                        // 右侧按钮，weight 等分空间，确保手柄始终居中。
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            trailingButton?.invoke()
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            contentWindowInsets = {
                val bars = if (fullscreen) {
                    WindowInsets.navigationBars
                } else {
                    WindowInsets.systemBars
                }
                bars.union(WindowInsets.ime)
            }
        ) {
            ProvideAppScaledDensity(appDensity) {
                if (bottomBar != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (fullscreen) Modifier.fillMaxHeight() else Modifier)
                            .then(contentModifier)
                    ) {
                        if (fixedHeader && title != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                BottomSheetHeader(title = title, icon = icon)
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(contentPadding)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = contentArrangement
                        ) {
                            if (!fixedHeader) {
                                BottomSheetHeader(title = title, icon = icon)
                            }
                            content()
                        }
                        bottomBar()
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (fullscreen) Modifier.fillMaxHeight() else Modifier)
                            .padding(contentPadding)
                            .then(if (fullscreen) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                            .then(contentModifier),
                        verticalArrangement = contentArrangement
                    ) {
                        BottomSheetHeader(title = title, icon = icon)
                        content()
                    }
                }
            }
        }
    }
}

/**
 * 管理底部弹窗的显隐、键盘和背景模糊逻辑。
 *
 * 当 [visible] 从 true 变为 false 时，先标记 isHiding，让 ModalBottomSheet
 * 播放退出动画；动画完成后只复位 isHiding。关闭回调由用户动作触发。
 *
 * @return 当前是否正处于隐藏动画中。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberBottomSheetHidingState(
    visible: Boolean,
    sheetState: SheetState,
    onHideStarted: () -> Unit,
): Boolean {
    var isHiding by remember { mutableStateOf(false) }
    var previousVisible by remember { mutableStateOf(visible) }

    // 当 visible 从 true 变为 false，标记正在隐藏。
    if (previousVisible && !visible && !isHiding) {
        isHiding = true
    }
    // 当 visible 变回 true（用户重新打开），重置隐藏状态。
    if (visible && isHiding) {
        isHiding = false
    }
    previousVisible = visible

    // 驱动 ModalBottomSheet 执行退出动画。
    // 使用 try-finally 确保即使 sheetState.hide() 被取消（如键盘弹出触发 expand()），
    // isHiding 也会被正确重置，避免 ModalBottomSheet 的 scrim 永久阻挡屏幕。
    LaunchedEffect(isHiding) {
        if (isHiding) {
            onHideStarted()
            // 只有 sheet 当前确实可见时才执行 hide 动画。
            // scrim/返回键路径下 ModalBottomSheet 已自行动画完毕，此时 sheetState 已 Hidden。
            try {
                if (sheetState.isVisible) {
                    sheetState.hide()
                }
            } finally {
                isHiding = false
            }
        }
    }

    // 键盘弹出时自动将半展开的 sheet 展开到全屏状态，避免输入框被输入法遮挡。
    // 当 isHiding 为 true 时跳过，防止 expand() 取消正在进行的 hide() 动画。
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible && !isHiding && sheetState.currentValue == SheetValue.PartiallyExpanded) {
            sheetState.expand()
        }
    }

    val sheetVisibilityState = LocalSheetVisibilityState.current
    DisposableEffect(visible, isHiding, sheetVisibilityState) {
        val shouldBeOpen = visible || isHiding
        if (shouldBeOpen) {
            sheetVisibilityState.open()
        }
        onDispose {
            if (shouldBeOpen) {
                sheetVisibilityState.close()
            }
        }
    }

    LaunchedEffect(sheetState, sheetVisibilityState) {
        snapshotFlow { sheetState.targetValue == SheetValue.Hidden }
            .distinctUntilChanged()
            .collect { isHiddenTarget ->
                sheetVisibilityState.blurProgress = if (isHiddenTarget) 0f else 1f
            }
    }

    return isHiding
}

/**
 * 底部弹窗的标题与图标区块。
 *
 * [title] 为 null 时不渲染；[icon] 为 null 时只显示标题。
 */
@Composable
private fun BottomSheetHeader(
    title: String?,
    icon: ImageVector?,
) {
    if (title == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 底部弹窗列表项的图标区域。
 *
 * 优先显示 [iconUrl]，其次显示 [icon]；两者都为 null 时用透明占位保持文字对齐。
 * 选中态与禁用态沿用现有配色规则。
 */
@Composable
private fun BottomSheetItemIconContainer(
    selected: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * 底部弹窗列表项的图标内容。
 */
@Composable
private fun BottomSheetItemIcon(
    icon: ImageVector?,
    iconUrl: String?,
    selected: Boolean,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val imageRequest = remember(iconUrl, context) {
        if (iconUrl != null) {
            ImageRequest.Builder(context)
                .data(iconUrl)
                .size(64)
                .crossfade(true)
                .build()
        } else {
            null
        }
    }

    if (iconUrl != null) {
        BottomSheetItemIconContainer(selected = selected) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Fit
            )
        }
    } else if (icon != null) {
        BottomSheetItemIconContainer(selected = selected) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else if (enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
                },
                modifier = Modifier.size(24.dp)
            )
        }
    } else if (selected) {
        BottomSheetItemIconContainer(selected = true) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    } else {
        Spacer(modifier = Modifier.size(40.dp))
    }
}

/**
 * 底部弹窗中使用的图文条目行。
 * 左侧为可选图标（带 primaryContainer 背景），右侧为标题文字。
 *
 * 当 [icon] 为 null 时，图标区域保留透明占位（40dp），确保文字对齐一致。
 *
 * 使用示例：
 * ```kotlin
 * BottomSheetItem(
 *     icon = Icons.Outlined.Store,
 *     title = "今日校园充值",
 *     onClick = { /* ... */ }
 * )
 * ```
 */
@Composable
fun BottomSheetItem(
    icon: ImageVector?,
    title: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    iconUrl: String? = null,
    containerColor: androidx.compose.ui.graphics.Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = containerColor ?: when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            enabled -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.2f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
                .then(if (!enabled) Modifier.alpha(0.38f) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BottomSheetItemIcon(
                icon = icon,
                iconUrl = iconUrl,
                selected = selected,
                enabled = enabled,
            )

            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                modifier = Modifier.weight(1f)
            )

            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}
