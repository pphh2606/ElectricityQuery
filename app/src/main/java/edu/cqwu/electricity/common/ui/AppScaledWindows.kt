package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun ProvideAppScaledDensity(
    density: Density,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDensity provides density,
        content = content,
    )
}

@Composable
internal fun AppScaledAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    // 与 LoadingDialog / BottomSheetDialog 同款：驱动全局 Haze 模糊背后的应用内容。
    // 组合进入（弹窗显示）即开启模糊，组合销毁（弹窗关闭）即复位。
    val sheetVisibilityState = LocalSheetVisibilityState.current
    DisposableEffect(sheetVisibilityState) {
        sheetVisibilityState.open()
        sheetVisibilityState.blurProgress = 1f
        onDispose {
            sheetVisibilityState.close()
            sheetVisibilityState.blurProgress = 0f
        }
    }

    val appDensity = LocalDensity.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            ProvideAppScaledDensity(appDensity) {
                confirmButton()
            }
        },
        modifier = modifier,
        dismissButton = dismissButton?.let { dismiss ->
            {
                ProvideAppScaledDensity(appDensity) {
                    dismiss()
                }
            }
        },
        icon = icon?.let { iconContent ->
            {
                ProvideAppScaledDensity(appDensity) {
                    iconContent()
                }
            }
        },
        title = title?.let { titleContent ->
            {
                ProvideAppScaledDensity(appDensity) {
                    titleContent()
                }
            }
        },
        text = text?.let { textContent ->
            {
                ProvideAppScaledDensity(appDensity) {
                    textContent()
                }
            }
        },
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppScaledDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val appDensity = LocalDensity.current
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(index = 0, count = 1),
        ) {
            ProvideAppScaledDensity(appDensity) {
                content()
            }
        }
    }
}

/**
 * M3E 菜单里的单个动作项。
 *
 * Expressive 菜单要求每一项自己声明形状：[MenuDefaults.standaloneItemShape]
 * 让每项都独立成一个全圆角块（而不是 [MenuDefaults.itemShape] 那种首尾圆、中间连体的分组卡片）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppScaledMenuItem(
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    DropdownMenuItem(
        onClick = onClick,
        text = text,
        leadingIcon = leadingIcon,
        shape = MenuDefaults.standaloneItemShape,
    )
}
