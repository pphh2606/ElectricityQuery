package edu.cqwu.electricity.settings.ui

import android.os.Build
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MotionPhotosAuto
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.settings.NightMode
import edu.cqwu.electricity.common.settings.PageTransition
import edu.cqwu.electricity.common.settings.ReduceMotion
import edu.cqwu.electricity.common.settings.ThemeColorSource
import edu.cqwu.electricity.common.settings.TopBarStyle
import edu.cqwu.electricity.common.settings.labelRes
import edu.cqwu.electricity.common.settings.MAX_FONT_SCALE
import edu.cqwu.electricity.common.settings.MAX_SHEET_BLUR_RADIUS
import edu.cqwu.electricity.common.settings.MIN_FONT_SCALE
import edu.cqwu.electricity.common.settings.MIN_SHEET_BLUR_RADIUS
import edu.cqwu.electricity.common.ui.isHazeBlurSupported
import edu.cqwu.electricity.common.ui.BottomSheetDialogV2
import edu.cqwu.electricity.common.ui.BottomSheetItem
import edu.cqwu.electricity.common.settings.LocalAppSettingsState
import edu.cqwu.electricity.theme.ui.currentTopBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizationScreen(
    onBack: () -> Unit,
    onNavigateToQrCodeSettings: () -> Unit = {},
    isDynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
) {
    val appSettings = LocalAppSettingsState.current
    var showNightModeDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showTopBarStyleDialog by remember { mutableStateOf(false) }
    var showPageTransitionDialog by remember { mutableStateOf(false) }
    var showReduceMotionDialog by remember { mutableStateOf(false) }
    var showFontSizeSlider by remember { mutableStateOf(false) }
    var draftFontScale by remember(appSettings.fontScale) { mutableFloatStateOf(appSettings.fontScale) }
    val displayedFontScale = if (showFontSizeSlider) draftFontScale else appSettings.fontScale
    val customSeedColor = (appSettings.colorSource as? ThemeColorSource.Custom)?.seedColor ?: Color(0xFF6750A4)
    val topBarColors = currentTopBarColors()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.personalization_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                Column {
                    SettingRow(
                        icon = Icons.Outlined.Contrast, title = stringResource(R.string.personalization_night_mode),
                        subtitle = stringResource(appSettings.nightMode.labelRes),
                        onClick = { showNightModeDialog = true },
                    )
                    SettingsSwitchEntry(
                        icon = Icons.Outlined.DarkMode,
                        title = stringResource(R.string.personalization_pure_black),
                        subtitle = stringResource(R.string.personalization_pure_black_desc),
                        checked = appSettings.pureBlack,
                        onCheckedChange = appSettings::updatePureBlack,
                    )
                    SettingsSwitchEntry(
                        icon = Icons.Outlined.Label,
                        title = stringResource(R.string.personalization_tab_label_selected_only),
                        subtitle = stringResource(R.string.personalization_tab_label_selected_only_desc),
                        checked = appSettings.tabLabelSelectedOnly,
                        onCheckedChange = appSettings::updateTabLabelSelectedOnly,
                    )
                    FontScaleRow(
                        title = stringResource(R.string.personalization_font_scale),
                        subtitle = stringResource(
                            R.string.personalization_font_scale_value,
                            (displayedFontScale * 100).roundToInt(),
                        ),
                        expanded = showFontSizeSlider,
                        onClick = {
                            showFontSizeSlider = !showFontSizeSlider
                            if (showFontSizeSlider) {
                                draftFontScale = appSettings.fontScale
                            }
                        },
                    )
                    AnimatedVisibility(visible = showFontSizeSlider) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp),
                        ) {
                            Slider(
                                value = draftFontScale,
                                onValueChange = { draftFontScale = it },
                                valueRange = MIN_FONT_SCALE..MAX_FONT_SCALE,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.personalization_font_scale_value,
                                        (MIN_FONT_SCALE * 100).toInt(),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.personalization_font_scale_value,
                                        (MAX_FONT_SCALE * 100).toInt(),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = {
                                        draftFontScale = 1f
                                        appSettings.updateFontScale(1f)
                                    },
                                ) {
                                    Text(stringResource(R.string.personalization_font_scale_default))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        appSettings.updateFontScale(draftFontScale)
                                        showFontSizeSlider = false
                                    },
                                ) {
                                    Text(stringResource(R.string.common_confirm))
                                }
                            }
                        }
                    }
                    // 系统不支持模糊时（Android 12 以下，或系统关掉了模糊）把这一项整体冻结：
                    // 与「动态取色不支持」的处理一致——只置灰、不可点，不隐藏，让用户知道有这项能力。
                    val blurSupported = isHazeBlurSupported()
                    val blurContentAlpha = if (blurSupported) 1f else 0.38f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = blurSupported) {
                                appSettings.updateSheetBlurEnabled(!appSettings.sheetBlurEnabled)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.BlurOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = blurContentAlpha),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.personalization_sheet_blur),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = blurContentAlpha)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.personalization_sheet_blur_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = blurContentAlpha)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            // 不支持时显示为「关」，避免出现"开着却点不动"的困惑
                            checked = appSettings.sheetBlurEnabled && blurSupported,
                            onCheckedChange = appSettings::updateSheetBlurEnabled,
                            enabled = blurSupported,
                        )
                    }
                    AnimatedVisibility(visible = blurSupported && appSettings.sheetBlurEnabled) {
                        Column {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.personalization_sheet_blur_strength),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = stringResource(
                                        R.string.personalization_sheet_blur_radius_value,
                                        appSettings.sheetBlurRadius
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Slider(
                                value = appSettings.sheetBlurRadius,
                                onValueChange = appSettings::updateSheetBlurRadius,
                                valueRange = MIN_SHEET_BLUR_RADIUS..MAX_SHEET_BLUR_RADIUS,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
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
                        icon = Icons.Outlined.AutoAwesome, title = stringResource(R.string.personalization_dynamic_color),
                        subtitle = if (isDynamicColorSupported) stringResource(R.string.personalization_dynamic_color_supported) else stringResource(R.string.personalization_dynamic_color_unsupported),
                        selected = appSettings.colorSource is ThemeColorSource.SystemDynamic,
                        enabled = isDynamicColorSupported,
                        onClick = { appSettings.updateColorSource(ThemeColorSource.SystemDynamic) },
                    )
                    ThemeColorRow(
                        icon = Icons.Outlined.Colorize, title = stringResource(R.string.personalization_custom_color),
                        subtitle = stringResource(R.string.personalization_custom_color_desc, customSeedColor.toHex()),
                        selected = appSettings.colorSource is ThemeColorSource.Custom,
                        onClick = {
                            showColorPicker = true
                        },
                    )
                    SettingRow(
                        icon = Icons.Outlined.FormatPaint, title = stringResource(R.string.personalization_topbar_color),
                        subtitle = stringResource(appSettings.topBarStyle.labelRes),
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
                    icon = Icons.Outlined.QrCode,
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
                        icon = Icons.Outlined.Animation, title = stringResource(R.string.personalization_page_transition),
                        subtitle = if (appSettings.reduceMotion == ReduceMotion.ON) {
                            "${stringResource(appSettings.pageTransition.labelRes)}${stringResource(R.string.personalization_reduce_motion_override)}"
                        } else {
                            stringResource(appSettings.pageTransition.labelRes)
                        },
                        onClick = { showPageTransitionDialog = true },
                    )
                    SettingRow(
                        icon = Icons.Outlined.MotionPhotosAuto, title = stringResource(R.string.personalization_reduce_motion),
                        subtitle = stringResource(appSettings.reduceMotion.labelRes),
                        onClick = { showReduceMotionDialog = true },
                    )
                }
            }
        }
    }

    NightModeSelectionDialog(
        visible = showNightModeDialog,
        current = appSettings.nightMode,
        onSelect = { mode -> appSettings.updateNightMode(mode); showNightModeDialog = false },
        onDismiss = { showNightModeDialog = false },
    )
    ColorPickerDialog(
        visible = showColorPicker,
        initialColor = customSeedColor,
        onColorPreview = { color -> appSettings.updateColorSource(ThemeColorSource.Custom(color)) },
        onDismiss = { showColorPicker = false },
    )
    SelectionDialog(
        visible = showTopBarStyleDialog,
        title = stringResource(R.string.personalization_topbar_color), current = appSettings.topBarStyle,
        entries = TopBarStyle.entries,
        displayText = { stringResource(it.labelRes) },
        onSelect = { appSettings.updateTopBarStyle(it); showTopBarStyleDialog = false },
        onDismiss = { showTopBarStyleDialog = false },
    )
    SelectionDialog(
        visible = showPageTransitionDialog,
        title = stringResource(R.string.personalization_page_transition), current = appSettings.pageTransition,
        entries = PageTransition.entries,
        displayText = { stringResource(it.labelRes) },
        onSelect = { appSettings.updatePageTransition(it); showPageTransitionDialog = false },
        onDismiss = { showPageTransitionDialog = false },
    )
    SelectionDialog(
        visible = showReduceMotionDialog,
        title = stringResource(R.string.personalization_reduce_motion), current = appSettings.reduceMotion,
        entries = ReduceMotion.entries,
        displayText = { stringResource(it.labelRes) },
        onSelect = { appSettings.updateReduceMotion(it); showReduceMotionDialog = false },
        onDismiss = { showReduceMotionDialog = false },
    )
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
            Icon(imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun FontScaleRow(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.TextFields,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            val arrowRotation by animateFloatAsState(
                targetValue = if (expanded) 90f else 0f,
                animationSpec = tween(300),
                label = "expandArrow",
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp).rotate(arrowRotation),
            )
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
private fun NightModeSelectionDialog(
    visible: Boolean = true,
    current: NightMode,
    onSelect: (NightMode) -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheetDialogV2(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.personalization_night_mode)
    ) {
        NightMode.entries.forEach { mode ->
            BottomSheetItem(
                icon = when (mode) {
                    NightMode.SYSTEM -> Icons.Outlined.AutoAwesome
                    NightMode.LIGHT -> Icons.Outlined.Contrast
                    NightMode.DARK -> Icons.Outlined.DarkMode
                },
                title = stringResource(mode.labelRes),
                selected = mode == current,
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
    visible: Boolean,
    initialColor: Color,
    onColorPreview: (Color) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val openedColor = remember(visible) { initialColor }
    // R/G/B 三个通道是唯一可变状态（0..255），HEX 文本与回传色都由它派生
    var red by remember(openedColor) { mutableFloatStateOf(openedColor.red * 255f) }
    var green by remember(openedColor) { mutableFloatStateOf(openedColor.green * 255f) }
    var blue by remember(openedColor) { mutableFloatStateOf(openedColor.blue * 255f) }
    var hexInput by remember(openedColor) { mutableStateOf(openedColor.toHex()) }
    var hexError by remember { mutableStateOf(false) }

    /** 任一通道变化：刷新 HEX 文本并立即回传（沿用即拖即应用的交互） */
    fun applyRgb(r: Float, g: Float, b: Float) {
        val color = Color(r / 255f, g / 255f, b / 255f)
        red = r
        green = g
        blue = b
        hexInput = color.toHex()
        hexError = false
        onColorPreview(color)
    }

    fun applyHex(text: String) {
        hexInput = text
        val parsed = parseHexColor(text)
        if (parsed == null) {
            hexError = true
        } else {
            applyRgb(parsed.red * 255f, parsed.green * 255f, parsed.blue * 255f)
        }
    }

    BottomSheetDialogV2(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.personalization_choose_color),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = hexInput,
                onValueChange = ::applyHex,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                label = { Text(stringResource(R.string.personalization_choose_color_hex)) },
                placeholder = { Text(stringResource(R.string.personalization_choose_color_hex_placeholder)) },
                isError = hexError,
                supportingText = if (hexError) {
                    { Text(stringResource(R.string.personalization_choose_color_hex_error)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
            )
            RgbSliderRow(
                label = stringResource(R.string.personalization_choose_color_red),
                value = red,
                gradient = Brush.horizontalGradient(
                    listOf(
                        Color(0f, green / 255f, blue / 255f),
                        Color(1f, green / 255f, blue / 255f),
                    ),
                ),
                onValueChange = { applyRgb(it, green, blue) },
            )
            RgbSliderRow(
                label = stringResource(R.string.personalization_choose_color_green),
                value = green,
                gradient = Brush.horizontalGradient(
                    listOf(
                        Color(red / 255f, 0f, blue / 255f),
                        Color(red / 255f, 1f, blue / 255f),
                    ),
                ),
                onValueChange = { applyRgb(red, it, blue) },
            )
            RgbSliderRow(
                label = stringResource(R.string.personalization_choose_color_blue),
                value = blue,
                gradient = Brush.horizontalGradient(
                    listOf(
                        Color(red / 255f, green / 255f, 0f),
                        Color(red / 255f, green / 255f, 1f),
                    ),
                ),
                onValueChange = { applyRgb(red, green, it) },
            )
        }
    }
}

/**
 * RGB 单通道滑块行：左侧通道字母、中间渐变轨道滑块、右侧当前数值（0..255）。
 *
 * 轨道用该通道的水平渐变自绘（[Slider] 的 track 槽位），thumb 仍取 M3 默认样式；
 * 自绘轨道只影响绘制，不影响滑块的点击与拖动手势。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RgbSliderRow(
    label: String,
    value: Float,
    gradient: Brush,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(gradient, CircleShape),
                )
            },
        )
        Text(
            text = value.roundToInt().toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End,
        )
    }
}

/**
 * 通用单选列表 Dialog
 */
@Composable
private fun <T> SelectionDialog(
    visible: Boolean = true,
    title: String,
    current: T,
    entries: List<T>,
    displayText: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheetDialogV2(
        visible = visible,
        onDismissRequest = onDismiss,
        title = title,
    ) {
        entries.forEach { entry ->
            BottomSheetItem(
                icon = if (entry == current) Icons.Outlined.CheckCircle else null,
                title = displayText(entry),
                selected = entry == current,
                onClick = {
                    onSelect(entry)
                    onDismiss()
                }
            )
        }
    }
}
