package edu.cqwu.electricity.ui.components

import android.os.Build
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

// ================================================================
//  通用底部弹窗包装器
// ================================================================

/**
 * 通用底部弹窗，封装了 MD3 ModalBottomSheet 的样板代码。
 *
 * 支持两种使用模式：
 * - **推荐**：通过 [visible] 参数控制显隐，退出时自动播放动画（类似原生 Dialog.dismiss()）
 * - 兼容：通过外部 `if (show)` 条件渲染（此时退出动画由 ModalBottomSheet 自身管理）
 *
 * @param visible 控制弹窗是否可见。当从 `true` 变为 `false` 时，会先播放退出动画，
 *                动画完成后再移除内部 ModalBottomSheet。默认为 `true`（始终渲染）。
 *                设为 `false` 时仅在退出动画完成后才真正移除，不会立即消失。
 * @param onDismissRequest 弹窗关闭动画完成后的回调，通常在此设置状态变量为 false。
 *                         对于 scrim 点击和返回键，此回调在动画播放后触发。
 * @param fullscreen 如果为 true，内容区将撑满全屏高度并支持滚动，
 *                   适合内容较多的弹窗。默认为 false。
 * @param leadingButton 拖拽手柄栏左侧的可选按钮（文字 / 图标），靠左边缘对齐。
 * @param trailingButton 拖拽手柄栏右侧的可选按钮（文字 / 图标），靠右边缘对齐。
 *
 * 使用示例（推荐 — visible 模式，选项点击也带退出动画）：
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
 *             Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.common_settings))
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
    /** 显式覆盖 skipPartiallyExpanded，默认根据 fullscreen 推断 */
    skipPartiallyExpanded: Boolean? = null,
    leadingButton: @Composable (() -> Unit)? = null,
    trailingButton: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    // Android 7+：skipPartiallyExpanded = false 支持拖拽中间态动画
    // Android 6：skipPartiallyExpanded = true 避免 AnchoredDraggableState
    //           偏移量在布局前未初始化导致的崩溃
    val computedSkip = skipPartiallyExpanded
        ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) !fullscreen else true
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = computedSkip
    )

    // 追踪 visible 从 true → false 的转换，以便在 composition 阶段
    // 同步设置 isHiding = true，防止 ModalBottomSheet 被立即移除
    var isHiding by remember { mutableStateOf(false) }
    var previousVisible by remember { mutableStateOf(visible) }

    // 当 visible 从 true 变为 false，标记正在隐藏
    if (previousVisible && !visible && !isHiding) {
        isHiding = true
    }
    // 当 visible 变回 true（用户重新打开），重置隐藏状态
    if (visible && isHiding) {
        isHiding = false
    }
    previousVisible = visible

    // 当 isHiding 为 true 时，驱动 ModalBottomSheet 执行退出动画
    LaunchedEffect(isHiding) {
        if (isHiding) {
            // 只有 sheet 当前确实可见时才执行 hide 动画
            // （scrim/返回键路径下 ModalBottomSheet 已自行动画完毕，此时 sheetState 已 Hidden）
            if (sheetState.isVisible) {
                sheetState.hide()
            }
            isHiding = false
            onDismissRequest()
        }
    }

    // 键盘弹出时自动将半展开的 sheet 展开到全屏状态，
    // 避免输入框被输入法遮挡
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible && sheetState.currentValue == SheetValue.PartiallyExpanded) {
            sheetState.expand()
        }
    }

    // visible 或 isHiding 任一为 true 时渲染 ModalBottomSheet
    if (visible || isHiding) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            sheetGesturesEnabled = sheetGesturesEnabled,
            dragHandle = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧按钮（weight 等分空间，确保手柄始终居中）
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        leadingButton?.invoke()
                    }

                    // 居中拖拽手柄（固定宽度，自然居中）
                    Box(contentAlignment = Alignment.Center) {
                        BottomSheetDefaults.DragHandle()
                    }

                    // 右侧按钮（weight 等分空间，确保手柄始终居中）
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        trailingButton?.invoke()
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            contentWindowInsets = { WindowInsets.systemBars.union(WindowInsets.ime) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fullscreen) Modifier.fillMaxHeight() else Modifier)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
                    .then(if (fullscreen) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (title != null) {
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
                content()
            }
        }
    }
}

// ================================================================
//  底部弹窗条目组件（图文行）
// ================================================================

/**
 * 底部弹窗中使用的图文条目行。
 * 左侧为可选图标（带 primaryContainer 背景），右侧为标题文字。
 *
 * 当 [icon] 为 null 时，图标区域保留透明占位（40dp），确保文字对齐一致。
 *
 * 使用示例：
 * ```kotlin
 * BottomSheetItem(
 *     icon = Icons.Default.Store,
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
            selected -> MaterialTheme.colorScheme.secondaryContainer
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
            // 图标区域（优先 iconUrl，其次 icon，都为 null 时用透明占位保持文字对齐）
            if (iconUrl != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(iconUrl)
                            .size(64)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            } else if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }

            // 标题文字
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

            // 尾部内容（可选）
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}
