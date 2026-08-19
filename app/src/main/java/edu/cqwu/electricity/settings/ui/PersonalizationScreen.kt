package edu.cqwu.electricity.settings.ui

import android.os.Build
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.settings.data.NightMode
import edu.cqwu.electricity.settings.data.PageTransition
import edu.cqwu.electricity.settings.data.ReduceMotion
import edu.cqwu.electricity.settings.data.ThemeColorSource
import edu.cqwu.electricity.settings.data.TopBarStyle
import edu.cqwu.electricity.settings.data.labelRes
import edu.cqwu.electricity.theme.ui.MAX_FONT_SCALE
import edu.cqwu.electricity.theme.ui.MAX_SHEET_BLUR_RADIUS
import edu.cqwu.electricity.theme.ui.MIN_FONT_SCALE
import edu.cqwu.electricity.theme.ui.MIN_SHEET_BLUR_RADIUS
import edu.cqwu.electricity.theme.ui.BottomSheetDialog
import edu.cqwu.electricity.theme.ui.BottomSheetItem
import edu.cqwu.electricity.theme.ui.LocalAppSettingsState
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
    var draftFontScale by remember(appSettings.fontScale) { mutableStateOf(appSettings.fontScale) }
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                appSettings.updatePureBlack(!appSettings.pureBlack)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.DarkMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.personalization_pure_black),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.personalization_pure_black_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = appSettings.pureBlack,
                            onCheckedChange = appSettings::updatePureBlack,
                        )
                    }
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                appSettings.updateSheetBlurEnabled(!appSettings.sheetBlurEnabled)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.BlurOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.personalization_sheet_blur),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.personalization_sheet_blur_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = appSettings.sheetBlurEnabled,
                            onCheckedChange = appSettings::updateSheetBlurEnabled,
                        )
                    }
                    if (appSettings.sheetBlurEnabled) {
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
                        subtitle = stringResource(R.string.personalization_custom_color_desc),
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
            Icon(
                imageVector = if (expanded) {
                    Icons.Outlined.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp),
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
    BottomSheetDialog(
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
    val initialHsv = remember(openedColor) { openedColor.toHsv() }
    var hue by remember(openedColor) { mutableStateOf(initialHsv.hue) }
    var saturation by remember(openedColor) { mutableStateOf(initialHsv.saturation) }
    var value by remember(openedColor) { mutableStateOf(initialHsv.value) }
    var selectedColor by remember(openedColor) { mutableStateOf(openedColor) }
    var hexInput by remember(openedColor) { mutableStateOf(openedColor.toHex()) }
    var hexError by remember { mutableStateOf(false) }

    fun updateSelectedColor(color: Color) {
        selectedColor = color
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
            val hsv = parsed.toHsv()
            hue = hsv.hue
            saturation = hsv.saturation
            value = hsv.value
            updateSelectedColor(parsed)
        }
    }

    BottomSheetDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.personalization_choose_color),
        fullscreen = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.personalization_choose_color_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(selectedColor, RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp),
                        ),
                )
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = ::applyHex,
                    modifier = Modifier.weight(1f),
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
            }
            Spacer(modifier = Modifier.height(4.dp))
            HsvSliderRow(
                label = stringResource(R.string.personalization_choose_color_hue),
                value = hue,
                valueRange = 0f..360f,
                onValueChange = { newHue ->
                    hue = newHue
                    updateSelectedColor(Color.hsv(newHue, saturation, value))
                },
            )
            HsvSliderRow(
                label = stringResource(R.string.personalization_choose_color_saturation),
                value = saturation,
                valueRange = 0f..1f,
                onValueChange = { newSaturation ->
                    saturation = newSaturation
                    updateSelectedColor(Color.hsv(hue, newSaturation, value))
                },
            )
            HsvSliderRow(
                label = stringResource(R.string.personalization_choose_color_value),
                value = value,
                valueRange = 0f..1f,
                onValueChange = { newValue ->
                    value = newValue
                    updateSelectedColor(Color.hsv(hue, saturation, newValue))
                },
            )
        }
    }
}

@Composable
private fun HsvSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
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
    BottomSheetDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = title
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
