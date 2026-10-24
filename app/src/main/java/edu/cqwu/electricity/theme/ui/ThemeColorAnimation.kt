package edu.cqwu.electricity.theme.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

/**
 * 换种子色/换风格/换规范时的色板过渡。
 *
 * 直接切换 ColorScheme 会让整个界面瞬间跳色，这里对每个颜色角色分别做颜色动画，
 * 由 Compose 在动画结束后停在目标值，因此不会影响后续渲染。
 *
 * @param enabled 关闭时（例如用户开启「减少动画」）直接返回原色板，不做任何动画。
 */
@Composable
fun ColorScheme.animateAsState(enabled: Boolean = true): ColorScheme {
    if (!enabled) return this
    val spec: AnimationSpec<Color> = spring(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy,
    )

    @Suppress("UNUSED_VARIABLE")
    val animated = this

    return copy(
        primary = animateColorAsState(primary, spec, label = "theme_primary").value,
        onPrimary = animateColorAsState(onPrimary, spec, label = "theme_onPrimary").value,
        primaryContainer = animateColorAsState(primaryContainer, spec, label = "theme_primaryContainer").value,
        onPrimaryContainer = animateColorAsState(onPrimaryContainer, spec, label = "theme_onPrimaryContainer").value,
        inversePrimary = animateColorAsState(inversePrimary, spec, label = "theme_inversePrimary").value,
        secondary = animateColorAsState(secondary, spec, label = "theme_secondary").value,
        onSecondary = animateColorAsState(onSecondary, spec, label = "theme_onSecondary").value,
        secondaryContainer = animateColorAsState(secondaryContainer, spec, label = "theme_secondaryContainer").value,
        onSecondaryContainer = animateColorAsState(onSecondaryContainer, spec, label = "theme_onSecondaryContainer").value,
        tertiary = animateColorAsState(tertiary, spec, label = "theme_tertiary").value,
        onTertiary = animateColorAsState(onTertiary, spec, label = "theme_onTertiary").value,
        tertiaryContainer = animateColorAsState(tertiaryContainer, spec, label = "theme_tertiaryContainer").value,
        onTertiaryContainer = animateColorAsState(onTertiaryContainer, spec, label = "theme_onTertiaryContainer").value,
        background = animateColorAsState(background, spec, label = "theme_background").value,
        onBackground = animateColorAsState(onBackground, spec, label = "theme_onBackground").value,
        surface = animateColorAsState(surface, spec, label = "theme_surface").value,
        onSurface = animateColorAsState(onSurface, spec, label = "theme_onSurface").value,
        surfaceVariant = animateColorAsState(surfaceVariant, spec, label = "theme_surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(onSurfaceVariant, spec, label = "theme_onSurfaceVariant").value,
        surfaceTint = animateColorAsState(surfaceTint, spec, label = "theme_surfaceTint").value,
        inverseSurface = animateColorAsState(inverseSurface, spec, label = "theme_inverseSurface").value,
        inverseOnSurface = animateColorAsState(inverseOnSurface, spec, label = "theme_inverseOnSurface").value,
        error = animateColorAsState(error, spec, label = "theme_error").value,
        onError = animateColorAsState(onError, spec, label = "theme_onError").value,
        errorContainer = animateColorAsState(errorContainer, spec, label = "theme_errorContainer").value,
        onErrorContainer = animateColorAsState(onErrorContainer, spec, label = "theme_onErrorContainer").value,
        outline = animateColorAsState(outline, spec, label = "theme_outline").value,
        outlineVariant = animateColorAsState(outlineVariant, spec, label = "theme_outlineVariant").value,
        scrim = animateColorAsState(scrim, spec, label = "theme_scrim").value,
        surfaceBright = animateColorAsState(surfaceBright, spec, label = "theme_surfaceBright").value,
        surfaceDim = animateColorAsState(surfaceDim, spec, label = "theme_surfaceDim").value,
        surfaceContainer = animateColorAsState(surfaceContainer, spec, label = "theme_surfaceContainer").value,
        surfaceContainerHigh = animateColorAsState(surfaceContainerHigh, spec, label = "theme_surfaceContainerHigh").value,
        surfaceContainerHighest = animateColorAsState(surfaceContainerHighest, spec, label = "theme_surfaceContainerHighest").value,
        surfaceContainerLow = animateColorAsState(surfaceContainerLow, spec, label = "theme_surfaceContainerLow").value,
        surfaceContainerLowest = animateColorAsState(surfaceContainerLowest, spec, label = "theme_surfaceContainerLowest").value,
        primaryFixed = animateColorAsState(primaryFixed, spec, label = "theme_primaryFixed").value,
        primaryFixedDim = animateColorAsState(primaryFixedDim, spec, label = "theme_primaryFixedDim").value,
        onPrimaryFixed = animateColorAsState(onPrimaryFixed, spec, label = "theme_onPrimaryFixed").value,
        onPrimaryFixedVariant = animateColorAsState(onPrimaryFixedVariant, spec, label = "theme_onPrimaryFixedVariant").value,
        secondaryFixed = animateColorAsState(secondaryFixed, spec, label = "theme_secondaryFixed").value,
        secondaryFixedDim = animateColorAsState(secondaryFixedDim, spec, label = "theme_secondaryFixedDim").value,
        onSecondaryFixed = animateColorAsState(onSecondaryFixed, spec, label = "theme_onSecondaryFixed").value,
        onSecondaryFixedVariant = animateColorAsState(onSecondaryFixedVariant, spec, label = "theme_onSecondaryFixedVariant").value,
        tertiaryFixed = animateColorAsState(tertiaryFixed, spec, label = "theme_tertiaryFixed").value,
        tertiaryFixedDim = animateColorAsState(tertiaryFixedDim, spec, label = "theme_tertiaryFixedDim").value,
        onTertiaryFixed = animateColorAsState(onTertiaryFixed, spec, label = "theme_onTertiaryFixed").value,
        onTertiaryFixedVariant = animateColorAsState(onTertiaryFixedVariant, spec, label = "theme_onTertiaryFixedVariant").value,
    )
}
