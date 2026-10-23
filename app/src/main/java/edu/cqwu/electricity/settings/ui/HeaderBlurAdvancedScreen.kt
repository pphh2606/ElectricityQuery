package edu.cqwu.electricity.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.settings.LocalAppSettingsState
import edu.cqwu.electricity.common.settings.MAX_HEADER_BLUR_ALPHA
import edu.cqwu.electricity.common.settings.MAX_HEADER_BLUR_BACKGROUND_ALPHA
import edu.cqwu.electricity.common.settings.MAX_HEADER_BLUR_NOISE
import edu.cqwu.electricity.common.settings.MAX_HEADER_BLUR_RADIUS
import edu.cqwu.electricity.common.settings.MIN_HEADER_BLUR_ALPHA
import edu.cqwu.electricity.common.settings.MIN_HEADER_BLUR_BACKGROUND_ALPHA
import edu.cqwu.electricity.common.settings.MIN_HEADER_BLUR_NOISE
import edu.cqwu.electricity.common.settings.MIN_HEADER_BLUR_RADIUS
import edu.cqwu.electricity.common.ui.BlurScaffold
import edu.cqwu.electricity.common.ui.transparentTopBarColors

/**
 * 标题栏毛玻璃的「高级模糊设置」。
 *
 * 这里只放 Haze 的参数本身，**名称保持与库 API 一致**（`blurRadius`、`noiseFactor`、
 * `alpha`、`backgroundColor` 的透明度、`performanceMode`），面向愿意自己调的进阶用户；
 * 普通用户只需要「个性化」里的那个开关，默认值就是全项目统一采用的观感。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderBlurAdvancedScreen(onBack: () -> Unit) {
    val appSettings = LocalAppSettingsState.current
    val topBarColors = transparentTopBarColors()

    BlurScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.advanced_blur_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
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
    ) { topInset ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(topInset + 8.dp))

            Text(
                text = stringResource(R.string.advanced_blur_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── HazeBlurStyle 的数值参数 ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    BlurParamRow(
                        name = "blurRadius",
                        description = stringResource(R.string.advanced_blur_desc_blur_radius),
                        value = appSettings.headerBlurRadius,
                        range = MIN_HEADER_BLUR_RADIUS..MAX_HEADER_BLUR_RADIUS,
                        unit = "dp",
                        decimals = 0,
                        onValueChange = appSettings::updateHeaderBlurRadius,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    BlurParamRow(
                        name = "noiseFactor",
                        description = stringResource(R.string.advanced_blur_desc_noise_factor),
                        value = appSettings.headerBlurNoise,
                        range = MIN_HEADER_BLUR_NOISE..MAX_HEADER_BLUR_NOISE,
                        unit = "",
                        decimals = 2,
                        onValueChange = appSettings::updateHeaderBlurNoise,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    BlurParamRow(
                        name = "alpha",
                        description = stringResource(R.string.advanced_blur_desc_alpha),
                        value = appSettings.headerBlurAlpha,
                        range = MIN_HEADER_BLUR_ALPHA..MAX_HEADER_BLUR_ALPHA,
                        unit = "",
                        decimals = 2,
                        onValueChange = appSettings::updateHeaderBlurAlpha,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    BlurParamRow(
                        name = "backgroundColor.alpha",
                        description = stringResource(R.string.advanced_blur_desc_background_alpha),
                        value = appSettings.headerBlurBackgroundAlpha,
                        range = MIN_HEADER_BLUR_BACKGROUND_ALPHA..MAX_HEADER_BLUR_BACKGROUND_ALPHA,
                        unit = "",
                        decimals = 2,
                        onValueChange = appSettings::updateHeaderBlurBackgroundAlpha,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── hazeBlur 的 performanceMode ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                BlurPerformanceOptions(
                    current = appSettings.headerBlurPerformance,
                    onSelect = appSettings::updateHeaderBlurPerformance,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = appSettings::resetHeaderBlurAdvanced,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.advanced_blur_reset))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
