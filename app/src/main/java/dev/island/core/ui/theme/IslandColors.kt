package dev.island.core.ui.theme

import androidx.compose.ui.graphics.Color
import dev.island.domain.model.IslandThemeMode

/**
 * Island color tokens.
 *
 * The island itself is always dark (it sits on top of the status bar area of any app), while the
 * app UI follows the same palette so the two feel like one product. Dynamic colour is used for
 * accents in the app and, optionally, for the island accent — never for the pill background,
 * which must stay readable over arbitrary app content.
 */
object IslandColors {
    val PureBlack = Color(0xFF000000)
    val DarkGray = Color(0xFF141518)
    val ElevatedDark = Color(0xFF1C1E22)
    val SurfaceDark = Color(0xFF101114)
    val OutlineDark = Color(0xFF2A2D33)

    val TextPrimary = Color(0xFFF4F6F8)
    val TextSecondary = Color(0xFFB4BAC3)
    val TextTertiary = Color(0xFF7C828C)
    val TextOnAccent = Color(0xFF08090B)

    val AccentDefault = Color(0xFF7CC4FF)
    val AccentGreen = Color(0xFF6BD968)
    val AccentAmber = Color(0xFFFFC46B)
    val AccentRed = Color(0xFFFF6B6B)
    val AccentViolet = Color(0xFFB79CFF)

    val ScrimLight = Color(0x14FFFFFF)
    val HairlineLight = Color(0x1FFFFFFF)
    val TrackDark = Color(0xFF2C2F35)

    /** App (settings/dashboard) palette. */
    val AppBackground = Color(0xFF08090B)
    val AppSurface = Color(0xFF111317)
    val AppSurfaceVariant = Color(0xFF171A1F)
    val AppOutline = Color(0xFF262A31)
}

/** Resolves the pill background for the user's theme choice. */
fun islandSurfaceColor(mode: IslandThemeMode, opacity: Float): Color {
    val base = when (mode) {
        IslandThemeMode.PURE_BLACK -> IslandColors.PureBlack
        IslandThemeMode.DARK_GRAY -> IslandColors.DarkGray
        IslandThemeMode.DYNAMIC -> IslandColors.DarkGray
    }
    return if (opacity >= 1f) base else base.copy(alpha = opacity.coerceIn(0.3f, 1f))
}

/**
 * Accent resolution: user accent → event accent → default blue.
 * Contrast is enforced so text on an accent never becomes unreadable (spec: accessibility).
 */
fun resolveAccent(userAccentArgb: Long?, eventAccentArgb: Long?): Color {
    val raw = userAccentArgb ?: eventAccentArgb
    if (raw == null) return IslandColors.AccentDefault
    val color = Color(raw.toULong().toLong())
    return ensureReadableAccent(color)
}

/** Pushes an accent away from the mid-luminance band where white/black text both fail. */
fun ensureReadableAccent(color: Color): Color {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return when {
        luminance in 0.18f..0.82f -> color
        luminance < 0.18f -> color.copy(
            red = (color.red + 0.22f).coerceAtMost(1f),
            green = (color.green + 0.22f).coerceAtMost(1f),
            blue = (color.blue + 0.22f).coerceAtMost(1f),
        )

        else -> color.copy(
            red = (color.red - 0.18f).coerceAtLeast(0f),
            green = (color.green - 0.18f).coerceAtLeast(0f),
            blue = (color.blue - 0.18f).coerceAtLeast(0f),
        )
    }
}

/** True when black text should be used on [background] (WCAG-style relative luminance test). */
fun shouldUseDarkTextOn(background: Color): Boolean {
    val luminance = 0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue
    return luminance > 0.55f
}
