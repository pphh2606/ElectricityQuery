package edu.cqwu.electricity.settings.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.settings.LocalAppSettingsState
import edu.cqwu.electricity.common.settings.MAX_SHEET_BLUR_ALPHA
import edu.cqwu.electricity.common.settings.MAX_SHEET_BLUR_NOISE
import edu.cqwu.electricity.common.settings.MAX_SHEET_BLUR_RADIUS
import edu.cqwu.electricity.common.settings.MIN_SHEET_BLUR_ALPHA
import edu.cqwu.electricity.common.settings.MIN_SHEET_BLUR_NOISE
import edu.cqwu.electricity.common.settings.MIN_SHEET_BLUR_RADIUS
import edu.cqwu.electricity.common.ui.BottomSheetDialogV2

/**
 * 弹窗背景模糊的「高级参数」底部面板。
 *
 * 与标题栏模糊是同一套思路：个性化页只留开关，点这一行在这里调 Haze 的原始参数。
 * 名称同样保持与库 API 一致；弹窗模糊直接作用于页面内容、没有底色，
 * 所以不提供 `backgroundColor`（面板里也写明了原因）。
 */
@Composable
fun SheetBlurAdvancedSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    val appSettings = LocalAppSettingsState.current

    BottomSheetDialogV2(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.sheet_blur_advanced_title),
        icon = Icons.Outlined.BlurOn,
    ) {
        Text(
            text = stringResource(R.string.advanced_blur_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))

        BlurParamRow(
            name = "blurRadius",
            description = stringResource(R.string.advanced_blur_desc_blur_radius),
            value = appSettings.sheetBlurRadius,
            range = MIN_SHEET_BLUR_RADIUS..MAX_SHEET_BLUR_RADIUS,
            unit = "dp",
            decimals = 0,
            onValueChange = appSettings::updateSheetBlurRadius,
            horizontalPadding = 0.dp,
        )
        HorizontalDivider()
        BlurParamRow(
            name = "noiseFactor",
            description = stringResource(R.string.advanced_blur_desc_noise_factor),
            value = appSettings.sheetBlurNoise,
            range = MIN_SHEET_BLUR_NOISE..MAX_SHEET_BLUR_NOISE,
            unit = "",
            decimals = 2,
            onValueChange = appSettings::updateSheetBlurNoise,
            horizontalPadding = 0.dp,
        )
        HorizontalDivider()
        BlurParamRow(
            name = "alpha",
            description = stringResource(R.string.advanced_blur_desc_alpha),
            value = appSettings.sheetBlurAlpha,
            range = MIN_SHEET_BLUR_ALPHA..MAX_SHEET_BLUR_ALPHA,
            unit = "",
            decimals = 2,
            onValueChange = appSettings::updateSheetBlurAlpha,
            horizontalPadding = 0.dp,
        )
        HorizontalDivider()
        BlurPerformanceOptions(
            current = appSettings.sheetBlurPerformance,
            onSelect = appSettings::updateSheetBlurPerformance,
            horizontalPadding = 0.dp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.sheet_blur_advanced_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = appSettings::resetSheetBlurAdvanced,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.advanced_blur_reset))
        }
    }
}
