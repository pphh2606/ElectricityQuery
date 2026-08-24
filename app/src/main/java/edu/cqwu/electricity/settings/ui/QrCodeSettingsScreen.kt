package edu.cqwu.electricity.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.settings.data.QrCodeColorMode
import edu.cqwu.electricity.theme.ui.QrCodeView
import edu.cqwu.electricity.theme.ui.LocalAppSettingsState
import edu.cqwu.electricity.theme.ui.currentTopBarColors

/** Corner radius slider range: 0..50 in 1% steps. */
private val CORNER_RADIUS_RANGE = 0f..50f

/**
 * 二维码设置页面
 *
 * 提供：
 * - 二维码实时预览（应用当前颜色和圆角设置）
 * - 颜色模式选择：MD3 主题色 / 黑色（夜间模式自动切换为白色）
 * - 圆角度滑块：固定整数点位 [0, 10, 20, 30, 40, 50]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeSettingsScreen(
    onBack: () -> Unit,
) {
    val appSettings = LocalAppSettingsState.current
    val topBarColors = currentTopBarColors()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // 深色模式下背景强制白色（确保扫码可读），前景色遵循用户设置
    val primaryColor = when (appSettings.qrCodeColorMode) {
        QrCodeColorMode.THEME_SNAKE -> MaterialTheme.colorScheme.primary
        QrCodeColorMode.MONOCHROME -> Color.Black
    }
    // 深色模式下 MD3 primary 偏浅（为深色背景设计），在白色背景上需加深
    val effectivePrimaryColor = if (isDarkTheme && appSettings.qrCodeColorMode == QrCodeColorMode.THEME_SNAKE) {
        val darkenFactor = 0.45f
        Color(
            primaryColor.red * darkenFactor,
            primaryColor.green * darkenFactor, primaryColor.blue * darkenFactor, primaryColor.alpha)
    } else {
        primaryColor
    }
    val qrBackgroundColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.surface

    // 圆角分数（0~50 int → 0.0f~0.5f float），开关关闭时为 0
    val cornerFraction = appSettings.effectiveQrCodeCornerRadius / 100f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.qrcode_settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
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
            // ── 二维码实时预览 ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    QrCodeView(
                        content = "QR Code Preview",
                        modifier = Modifier.size(240.dp),
                        squareCornerFraction = cornerFraction,
                        primaryColor = effectivePrimaryColor,
                        backgroundColor = qrBackgroundColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 颜色选择 ──
            SectionTitle(title = stringResource(R.string.qrcode_settings_style))
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    ColorModeRow(
                        title = stringResource(R.string.qrcode_settings_md3_theme),
                        subtitle = stringResource(R.string.qrcode_settings_md3_theme_desc),
                        selected = appSettings.qrCodeColorMode == QrCodeColorMode.THEME_SNAKE,
                        onClick = { appSettings.updateQrCodeColorMode(QrCodeColorMode.THEME_SNAKE) },
                        colorPreview = MaterialTheme.colorScheme.primary,
                    )
                    ColorModeRow(
                        title = stringResource(R.string.qrcode_settings_black),
                        subtitle = stringResource(R.string.qrcode_settings_black_desc),
                        selected = appSettings.qrCodeColorMode == QrCodeColorMode.MONOCHROME,
                        onClick = { appSettings.updateQrCodeColorMode(QrCodeColorMode.MONOCHROME) },
                        colorPreview = if (isDarkTheme) Color.White else Color.Black,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 圆角度 ──
            SectionTitle(title = stringResource(R.string.qrcode_settings_corner_radius))
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                appSettings.updateQrCodeCornerRadiusEnabled(
                                    !appSettings.qrCodeCornerRadiusEnabled,
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FormatPaint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.qrcode_settings_corner_radius_switch),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.qrcode_settings_corner_radius_switch_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = appSettings.qrCodeCornerRadiusEnabled,
                            onCheckedChange = appSettings::updateQrCodeCornerRadiusEnabled,
                        )
                    }
                    AnimatedVisibility(visible = appSettings.qrCodeCornerRadiusEnabled) {
                        Column {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.qrcode_settings_corner_radius),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${"%.1f".format(appSettings.qrCodeCornerRadius)}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Slider(
                                value = appSettings.qrCodeCornerRadius,
                                onValueChange = appSettings::updateQrCodeCornerRadius,
                                valueRange = CORNER_RADIUS_RANGE,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 屏幕高亮 ──
            SectionTitle(title = stringResource(R.string.qrcode_settings_display))
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            appSettings.updateQrScreenBrightnessEnabled(
                                !appSettings.qrScreenBrightnessEnabled,
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BrightnessHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.qrcode_settings_screen_brightness),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.qrcode_settings_screen_brightness_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                        Switch(
                            checked = appSettings.qrScreenBrightnessEnabled,
                            onCheckedChange = appSettings::updateQrScreenBrightnessEnabled,
                        )
                }
            }
        }
    }
}

/**
 * 颜色模式选择行 — 与 [PersonalizationScreen] 的 [ThemeColorRow] 风格一致
 */
@Composable
private fun ColorModeRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    colorPreview: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colorPreview, RoundedCornerShape(6.dp)),
        )
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
        Spacer(modifier = Modifier.width(8.dp))
        RadioButton(selected = selected, onClick = null)
    }
}

/**
 * 分区标题 — 与 [PersonalizationScreen] 复用相同样式
 */
@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}
