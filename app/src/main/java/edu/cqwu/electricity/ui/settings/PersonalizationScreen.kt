package edu.cqwu.electricity.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.MotionPhotosAuto
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.data.local.NightMode
import edu.cqwu.electricity.data.local.PageTransition
import edu.cqwu.electricity.data.local.ReduceMotion
import edu.cqwu.electricity.data.local.ThemeColorSource
import edu.cqwu.electricity.data.local.TopBarStyle
import edu.cqwu.electricity.data.local.displayName
import edu.cqwu.electricity.ui.components.BottomSheetDialog
import edu.cqwu.electricity.ui.components.BottomSheetItem
import edu.cqwu.electricity.ui.theme.toTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizationScreen(
    onBack: () -> Unit,
    currentNightMode: NightMode,
    onNightModeChange: (NightMode) -> Unit,
    currentColorSource: ThemeColorSource,
    onColorSourceChange: (ThemeColorSource) -> Unit,
    currentPageTransition: PageTransition,
    onPageTransitionChange: (PageTransition) -> Unit,
    currentReduceMotion: ReduceMotion,
    onReduceMotionChange: (ReduceMotion) -> Unit,
    currentTopBarStyle: TopBarStyle,
    onTopBarStyleChange: (TopBarStyle) -> Unit,
    onNavigateToQrCodeSettings: () -> Unit = {},
    isDynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
) {
    var showNightModeDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showTopBarStyleDialog by remember { mutableStateOf(false) }
    var showPageTransitionDialog by remember { mutableStateOf(false) }
    var showReduceMotionDialog by remember { mutableStateOf(false) }
    val customSeedColor = (currentColorSource as? ThemeColorSource.Custom)?.seedColor ?: Color(0xFF6750A4)
    val topBarColors = currentTopBarStyle.toTopAppBarColors(MaterialTheme.colorScheme)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.personalization_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = topBarColors,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // 显示设置
            SectionTitle(title = stringResource(R.string.personalization_display))
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                SettingRow(
                    icon = Icons.Default.Contrast, title = stringResource(R.string.personalization_night_mode),
                    subtitle = currentNightMode.displayName,
                    onClick = { showNightModeDialog = true },
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            // 主题颜色
            SectionTitle(title = stringResource(R.string.personalization_theme_color))
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    ThemeColorRow(
                        icon = Icons.Default.AutoAwesome, title = stringResource(R.string.personalization_dynamic_color),
                        subtitle = if (isDynamicColorSupported) stringResource(R.string.personalization_dynamic_color_supported) else stringResource(R.string.personalization_dynamic_color_unsupported),
                        selected = currentColorSource is ThemeColorSource.SystemDynamic,
                        enabled = isDynamicColorSupported,
                        onClick = { onColorSourceChange(ThemeColorSource.SystemDynamic) },
                    )
                    ThemeColorRow(
                        icon = Icons.Default.Colorize, title = stringResource(R.string.personalization_custom_color),
                        subtitle = stringResource(R.string.personalization_custom_color_desc),
                        selected = currentColorSource is ThemeColorSource.Custom,
                        onClick = {
                            showColorPicker = true
                        },
                    )
                    SettingRow(
                        icon = Icons.Default.FormatPaint, title = stringResource(R.string.personalization_topbar_color),
                        subtitle = currentTopBarStyle.displayName,
                        onClick = { showTopBarStyleDialog = true },
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // 二维码设置
            SectionTitle(title = stringResource(R.string.personalization_qrcode_settings))
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                SettingRow(
                    icon = Icons.Default.QrCode,
                    title = stringResource(R.string.personalization_qrcode_settings),
                    subtitle = stringResource(R.string.personalization_qrcode_desc),
                    onClick = onNavigateToQrCodeSettings,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            // 动画设置
            SectionTitle(title = stringResource(R.string.personalization_animation))
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    SettingRow(
                        icon = Icons.Default.Animation, title = stringResource(R.string.personalization_page_transition),
                        subtitle = if (currentReduceMotion == ReduceMotion.ON) {
                            "${currentPageTransition.displayName}${stringResource(R.string.personalization_reduce_motion_override)}"
                        } else {
                            currentPageTransition.displayName
                        },
                        onClick = { showPageTransitionDialog = true },
                    )
                    SettingRow(
                        icon = Icons.Default.MotionPhotosAuto, title = stringResource(R.string.personalization_reduce_motion),
                        subtitle = currentReduceMotion.displayName,
                        onClick = { showReduceMotionDialog = true },
                    )
                }
            }
        }
    }

    if (showNightModeDialog) {
        NightModeSelectionDialog(
            onSelect = { mode -> onNightModeChange(mode); showNightModeDialog = false },
            onDismiss = { showNightModeDialog = false },
        )
    }
    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = customSeedColor,
            onColorPreview = { color -> onColorSourceChange(ThemeColorSource.Custom(color)) },
            onColorSelected = { color ->
                onColorSourceChange(ThemeColorSource.Custom(color))
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }
    if (showTopBarStyleDialog) {
        SelectionDialog(
            title = stringResource(R.string.personalization_topbar_color), current = currentTopBarStyle,
            entries = TopBarStyle.entries,
            displayText = { it.displayName },
            onSelect = { onTopBarStyleChange(it); showTopBarStyleDialog = false },
            onDismiss = { showTopBarStyleDialog = false },
        )
    }
    if (showPageTransitionDialog) {
        SelectionDialog(
            title = stringResource(R.string.personalization_page_transition), current = currentPageTransition,
            entries = PageTransition.entries,
            displayText = { it.displayName },
            onSelect = { onPageTransitionChange(it); showPageTransitionDialog = false },
            onDismiss = { showPageTransitionDialog = false },
        )
    }
    if (showReduceMotionDialog) {
        SelectionDialog(
            title = stringResource(R.string.personalization_reduce_motion), current = currentReduceMotion,
            entries = ReduceMotion.entries,
            displayText = { it.displayName },
            onSelect = { onReduceMotionChange(it); showReduceMotionDialog = false },
            onDismiss = { showReduceMotionDialog = false },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ThemeColorRow(icon: ImageVector, title: String, subtitle: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            trailing?.invoke()
            Spacer(modifier = Modifier.width(8.dp))
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        }
    }
}

@Composable
private fun NightModeSelectionDialog(onSelect: (NightMode) -> Unit, onDismiss: () -> Unit) {
    BottomSheetDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.personalization_night_mode)
    ) {
        NightMode.entries.forEach { mode ->
            BottomSheetItem(
                icon = when (mode) {
                    NightMode.SYSTEM -> Icons.Default.AutoAwesome
                    NightMode.LIGHT -> Icons.Default.Contrast
                    NightMode.DARK -> Icons.Default.DarkMode
                },
                title = mode.displayName,
                onClick = {
                    onSelect(mode)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun ColorPickerDialog(
    initialColor: Color,
    onColorPreview: (Color) -> Unit = {},
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val presetColors = remember {
        listOf(Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
            Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF00BCD4),
            Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39),
            Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722),
            Color(0xFF795548), Color(0xFF607D8B))
    }
    var selectedColor by remember(initialColor) { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.personalization_choose_color), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(text = stringResource(R.string.personalization_choose_color_desc), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    presetColors.chunked(6).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { color ->
                                val isSelected = selectedColor == color
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(color, CircleShape)
                                        .border(width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            shape = CircleShape).clickable {
                                                selectedColor = color
                                                onColorPreview(color)  // ⭐ 即时预览
                                            },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onColorSelected(selectedColor); onDismiss() }) { Text(stringResource(R.string.common_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

/**
 * 通用单选列表 Dialog
 */
@Composable
private fun <T> SelectionDialog(
    title: String,
    current: T,
    entries: List<T>,
    displayText: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheetDialog(
        onDismissRequest = onDismiss,
        title = title
    ) {
        entries.forEach { entry ->
            BottomSheetItem(
                icon = if (entry == current) Icons.Default.CheckCircle else null,
                title = displayText(entry),
                onClick = {
                    onSelect(entry)
                    onDismiss()
                }
            )
        }
    }
}
