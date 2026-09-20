package dev.island.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.island.domain.model.AnimationStyle

/**
 * Centralised design tokens. No magic numbers in composables: every spacing, corner, elevation
 * and motion value comes from here (spec §79).
 */
object IslandSpacing {
    val xxxs: Dp = 1.dp
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp
}

object IslandDimensions {
    /** Collapsed pill defaults; the real values are derived by IslandMetrics. */
    val collapsedWidth: Dp = 148.dp
    val collapsedHeight: Dp = 36.dp
    val collapsedMinWidth: Dp = 96.dp
    val collapsedMaxWidth: Dp = 210.dp

    val expandedMaxWidth: Dp = 420.dp
    val sideMargin: Dp = 12.dp

    val iconSize: Dp = 18.dp
    val iconSizeLarge: Dp = 24.dp
    val albumArtCollapsed: Dp = 24.dp
    val albumArtExpanded: Dp = 56.dp
    val controlSize: Dp = 40.dp
    val controlSizeCompact: Dp = 32.dp
    val progressHeight: Dp = 3.dp
    val progressHeightExpanded: Dp = 4.dp
    val queueDotSize: Dp = 4.dp
    val queueStripWidth: Dp = 44.dp
    val stackedGap: Dp = 10.dp
    val waveformBarWidth: Dp = 2.dp
    val waveformBarHeight: Dp = 10.dp

    /** Minimum touch target, per Android accessibility guidance. */
    val minTouchTarget: Dp = 48.dp
    val appIconSize: Dp = 20.dp
    val appIconSizeExpanded: Dp = 40.dp
}

object IslandCorners {
    val pill: Dp = 999.dp
    val expanded: Dp = 26.dp
    val card: Dp = 20.dp
    val chip: Dp = 12.dp
    val control: Dp = 14.dp
    val artwork: Dp = 8.dp
}

object IslandElevation {
    /** The island uses almost no shadow: a dark pill on a dark status bar needs no elevation. */
    val none: Dp = 0.dp
    val island: Dp = 2.dp
    val islandExpanded: Dp = 8.dp
    val card: Dp = 1.dp
}

object IslandTypography {
    val family: FontFamily = FontFamily.Default

    val collapsedTitle = TextStyle(
        fontFamily = family,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
        lineHeight = 16.sp,
    )
    val collapsedSubtitle = TextStyle(
        fontFamily = family,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
        lineHeight = 13.sp,
    )
    val expandedTitle = TextStyle(
        fontFamily = family,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        lineHeight = 20.sp,
    )
    val expandedSubtitle = TextStyle(
        fontFamily = family,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeight = 17.sp,
    )
    val timerLarge = TextStyle(
        fontFamily = family,
        fontSize = 34.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-1).sp,
        lineHeight = 38.sp,
        fontFeatureSettings = "tnum",
    )
    val timerMedium = TextStyle(
        fontFamily = family,
        fontSize = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.4).sp,
        lineHeight = 26.sp,
        fontFeatureSettings = "tnum",
    )
    val label = TextStyle(
        fontFamily = family,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        lineHeight = 14.sp,
    )
    val caption = TextStyle(
        fontFamily = family,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
        lineHeight = 12.sp,
    )
}

/**
 * Motion tokens. Every duration is divided by the user's animation speed, and the whole system
 * collapses to short fades when "Reduce motion" (or the system animator scale) is active.
 */
object IslandMotion {
    const val EXPAND_STIFFNESS = 520f
    const val COLLAPSE_STIFFNESS = 700f
    const val BOUNCE_STIFFNESS = 380f
    const val DEFAULT_DAMPING = 0.86f
    const val SPRINGY_DAMPING = 0.62f
    const val SNAPPY_DAMPING = 0.98f
    const val VISIBILITY_THRESHOLD = 0.01f

    val contentCrossfadeMs = 180
    val expandDurationMs = 320
    val collapseDurationMs = 260
    val introHoldMs = 2600
    val waveformPeriodMs = 1100
    val chargePulsePeriodMs = 2400
    val queueSwitchMs = 220

    fun dampingFor(style: AnimationStyle): Float = when (style) {
        AnimationStyle.SPRINGY -> SPRINGY_DAMPING
        AnimationStyle.SMOOTH -> DEFAULT_DAMPING
        AnimationStyle.SNAPPY -> SNAPPY_DAMPING
    }
}
