package edu.cqwu.electricity.common.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 通用底部弹窗，封装 MD3 ModalBottomSheet 的样板代码。
 *
 * 通过 [visible] 参数控制显隐，退出时自动播放动画，类似原生 Dialog.dismiss()。
 *
 * 高度上限为「屏高 − 状态栏」：内容撑满时顶边停在状态栏下沿，圆角不会被无圆角机型的屏幕边缘切掉。
 * 状态栏已由顶层 padding 让出，故内容 inset 只取导航栏与输入法，重复计入状态栏会出现双倍顶部留白。
 *
 * @param visible 控制弹窗是否可见。当从 `true` 变为 `false` 时，会先播放退出动画，
 *                动画完成后再移除内部 ModalBottomSheet。默认为 `true`（始终渲染）。
 *                设为 `false` 时仅在退出动画完成后才真正移除，不会立即消失。
 * @param onDismissRequest 用户请求关闭弹窗时触发的回调，通常在此将状态变量设为 false。
 *                         组件不会在内部隐藏动画结束后再次补调该回调。
 * @param skipPartiallyExpanded 是否跳过半展开，默认 true（打开直接显示内容高度）；内容较高时可传 false 让弹窗先停在半展开位置。
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
    // ── 显隐与标题 ──
    visible: Boolean = true,
    onDismissRequest: () -> Unit,
    title: String? = null,
    icon: ImageVector? = null,
    // ── 布局 ──
    /** 是否跳过半展开，默认 true（打开直接显示内容高度）；内容较高时可传 false 先停在半展开位置。 */
    skipPartiallyExpanded: Boolean? = null,
    // ── 手柄栏按钮 ──
    leadingButton: @Composable (() -> Unit)? = null,
    trailingButton: @Composable (() -> Unit)? = null,
    // ── 内容微调 ──
    contentModifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    contentArrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp),
    // ── 生命周期 ──
    onHideStarted: () -> Unit = {},
    content: @Composable () -> Unit
) {
    // 兼容 Android 7+ 的 sheet 状态行为；默认跳过半展开，直接显示内容高度。
    val computedSkip = skipPartiallyExpanded ?: true
    val appDensity = LocalDensity.current

    // 打开时重建一轮状态：上次关闭若被手势打断，SheetState 会残留 Expanded/Settling，
    // 复用会让 ModalBottomSheet 认为已展开、跳过弹出动画。
    // 用 visible 的上升沿在同一帧内递增（放进 LaunchedEffect 会晚一帧，
    // 第一帧先用旧 key 挂载、下一帧再重建，点按到弹出会多出一段间隔）。
    var lastVisible by remember { mutableStateOf(visible) }
    var openGeneration by remember { mutableIntStateOf(0) }
    if (lastVisible != visible) {
        lastVisible = visible
        if (visible) openGeneration++
    }

    key(openGeneration) {
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
            // 顶部让出状态栏高度：撑满时顶边停在状态栏下沿。
            modifier = Modifier.statusBarsPadding(),
            sheetState = sheetState,
            // 弹出动画完成前不响应拖拽：否则拖动会打断 show()，松手时可能被判定为关闭
            sheetGesturesEnabled = sheetState.currentValue != SheetValue.Hidden,
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            contentWindowInsets = {
                WindowInsets.navigationBars.union(WindowInsets.ime)
            }
        ) {
            ProvideAppScaledDensity(appDensity) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(contentPadding)
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
 * 单选列表底部弹窗（语言选择弹窗同款形式）。
 *
 * 相对全屏弹窗的优势：
 * - 内容不高时高度自适应，不撑满全屏、底部无留白；
 * - 内容超过半屏时先停在半展开位置，可上滑看全；
 * - 标题、列表项与底部空白整体可滚动。
 *
 * @param visible 控制弹窗是否可见
 * @param onDismissRequest 关闭回调
 * @param title 标题（随内容滚动），null 不显示
 * @param icon 标题上方图标，null 只显示标题
 * @param leadingButton 手柄栏左侧按钮（可选）
 * @param trailingButton 手柄栏右侧按钮（可选）
 * @param content 列表项内容（如多个 [BottomSheetItem]）
 */
@Composable
fun BottomSheetDialogV2(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    icon: ImageVector? = null,
    leadingButton: @Composable (() -> Unit)? = null,
    trailingButton: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    BottomSheetDialog(
        visible = visible,
        onDismissRequest = onDismissRequest,
        skipPartiallyExpanded = false,
        leadingButton = leadingButton,
        trailingButton = trailingButton,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (title != null) {
                BottomSheetHeader(title = title, icon = icon)
            }
            content()
            // 原内容区底部空白，随内容一起滚动
            Spacer(modifier = Modifier.height(24.dp))
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

    // 无条件 hide()：弹出动画尚未结束时 isVisible 也是 false，
    // 用它当守卫会跳过 hide、把状态留在 Settling，下一次打开就丢了弹出动画
    LaunchedEffect(isHiding) {
        if (isHiding) {
            onHideStarted()
            try {
                sheetState.hide()
            } finally {
                isHiding = false
            }
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

/** 弹窗列表项组首尾的圆角，与设置页的分段卡片一致。 */
private val SheetItemCorner = 16.dp

/** 组内相邻项之间的连接角。 */
private val SheetItemInnerCorner = 5.dp

/** 分组内相邻项的缝隙。 */
private val SheetItemGap = 2.dp

/** 弹窗列表项按下时的圆角，与设置行按下后的形状一致。 */
private val SheetItemPressedCorner = 16.dp

/** 列表项的四角圆角。 */
private data class SheetItemCorners(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomEnd: Dp,
    val bottomStart: Dp,
) {
    fun toShape(): Shape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)

    /** 按下时四角一起向 [SheetItemPressedCorner] 过渡。 */
    fun pressed(progress: Float): SheetItemCorners = SheetItemCorners(
        topStart = lerp(topStart.value, SheetItemPressedCorner.value, progress).dp,
        topEnd = lerp(topEnd.value, SheetItemPressedCorner.value, progress).dp,
        bottomEnd = lerp(bottomEnd.value, SheetItemPressedCorner.value, progress).dp,
        bottomStart = lerp(bottomStart.value, SheetItemPressedCorner.value, progress).dp,
    )
}

/** 单个列表项（未分组）的四角圆角。 */
private val SingleItemCorners = SheetItemCorners(
    topStart = SheetItemCorner,
    topEnd = SheetItemCorner,
    bottomEnd = SheetItemCorner,
    bottomStart = SheetItemCorner,
)

/** 当前列表项在分组中的圆角，由 [SheetItemGroup] 注入。 */
private val LocalSheetItemCorners = staticCompositionLocalOf { SingleItemCorners }

/** 组内第 [index] 项（共 [count] 项）的圆角：首尾大圆角，中间小连接角。 */
private fun sheetItemCorners(index: Int, count: Int): SheetItemCorners = when {
    count <= 1 -> SingleItemCorners
    index == 0 -> SingleItemCorners.copy(
        bottomEnd = SheetItemInnerCorner,
        bottomStart = SheetItemInnerCorner,
    )

    index == count - 1 -> SingleItemCorners.copy(
        topStart = SheetItemInnerCorner,
        topEnd = SheetItemInnerCorner,
    )

    else -> SheetItemCorners(
        topStart = SheetItemInnerCorner,
        topEnd = SheetItemInnerCorner,
        bottomEnd = SheetItemInnerCorner,
        bottomStart = SheetItemInnerCorner,
    )
}

/**
 * [SheetItemGroup] 的作用域，按顺序收集列表项。
 */
class SheetItemGroupScope internal constructor() {
    internal val items = mutableListOf<@Composable () -> Unit>()

    fun item(content: @Composable () -> Unit) {
        items += content
    }
}

/**
 * 弹窗内成组排列的列表项。
 *
 * 组首尾使用 16dp 圆角、组内相邻项使用 5dp 连接角、项间留 2dp 缝隙，
 * 与设置页的分段卡片保持一致。
 */
@Composable
fun SheetItemGroup(
    modifier: Modifier = Modifier,
    content: @Composable SheetItemGroupScope.() -> Unit,
) {
    val scope = remember { SheetItemGroupScope() }
    scope.items.clear()
    scope.content()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SheetItemGap),
    ) {
        scope.items.forEachIndexed { index, item ->
            CompositionLocalProvider(
                LocalSheetItemCorners provides sheetItemCorners(index, scope.items.size)
            ) {
                item()
            }
        }
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
    val tint = when {
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    when {
        iconUrl != null -> AsyncImage(
            model = imageRequest,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Fit
        )

        icon != null -> Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )

        selected -> Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )

        else -> Spacer(modifier = Modifier.size(24.dp))
    }
}

/**
 * 底部弹窗中使用的图文条目行。
 * 左侧为可选图标，右侧为标题文字。
 *
 * 当 [icon] 为 null 时，图标区域保留 24dp 透明占位，确保文字对齐一致。
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
    containerColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "sheetItemPress",
    )
    val animatedShape = LocalSheetItemCorners.current.pressed(pressProgress).toShape()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(animatedShape)
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(0.dp),
            color = containerColor ?: when {
                selected -> MaterialTheme.colorScheme.secondaryContainer
                enabled -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.2f)
            },
            interactionSource = interaction,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp * LocalDensity.current.fontScale)
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
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        selected -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )

                if (trailingContent != null) {
                    trailingContent()
                }
            }
        }
    }
}
