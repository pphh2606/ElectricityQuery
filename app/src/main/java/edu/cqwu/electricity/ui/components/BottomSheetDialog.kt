package edu.cqwu.electricity.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.wrapContentSize
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
 * @param fullscreen 如果为 true，内容区将撑满全屏高度并支持滚动，
 *                   适合内容较多的弹窗。默认为 false。
 * @param leadingButton 拖拽手柄栏左侧的可选按钮（文字 / 图标），靠左边缘对齐。
 * @param trailingButton 拖拽手柄栏右侧的可选按钮（文字 / 图标），靠右边缘对齐。
 *
 * 使用示例：
 * ```kotlin
 * if (showSheet) {
 *     BottomSheetDialog(
 *         onDismissRequest = { showSheet = false },
 *         title = "选择选项"
 *     ) {
 *         // 自定义内容
 *     }
 * }
 * ```
 *
 * 带手柄按钮的示例：
 * ```kotlin
 * BottomSheetDialog(
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

    // 键盘弹出时自动将半展开的 sheet 展开到全屏状态，
    // 避免输入框被输入法遮挡
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible && sheetState.currentValue == SheetValue.PartiallyExpanded) {
            sheetState.expand()
        }
    }

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
                }
            )
        }
    }
}
