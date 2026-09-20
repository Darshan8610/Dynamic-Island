package dev.island.core.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.island.domain.model.IslandAppearance

/** App typography: quiet, high-contrast, no display faces. */
private val IslandAppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 30.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.6).sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
        color = IslandColors.TextSecondary,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
        lineHeight = 14.sp,
        color = IslandColors.TextTertiary,
    ),
)

private val IslandAppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Runtime appearance, available to the overlay and to the in-app preview. */
@Immutable
data class IslandSurfaceTokens(
    val appearance: IslandAppearance = IslandAppearance(),
    val reduceMotion: Boolean = false,
)

val LocalIslandSurface = staticCompositionLocalOf { IslandSurfaceTokens() }

/**
 * Dark-first Material 3 theme. Dynamic colour is opt-in and only affects the app chrome:
 * the pill stays dark so it remains readable over any app's status bar area.
 */
@Composable
fun IslandAppTheme(
    appearance: IslandAppearance = IslandAppearance(),
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = if (useDynamic) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme(
            primary = appearance.accentArgb?.let { androidx.compose.ui.graphics.Color(it) } ?: IslandColors.AccentDefault,
            onPrimary = IslandColors.TextOnAccent,
            primaryContainer = IslandColors.ElevatedDark,
            onPrimaryContainer = IslandColors.TextPrimary,
            secondary = IslandColors.AccentViolet,
            onSecondary = IslandColors.TextOnAccent,
            secondaryContainer = IslandColors.AppSurfaceVariant,
            onSecondaryContainer = IslandColors.TextPrimary,
            tertiary = IslandColors.AccentGreen,
            background = IslandColors.AppBackground,
            onBackground = IslandColors.TextPrimary,
            surface = IslandColors.AppSurface,
            onSurface = IslandColors.TextPrimary,
            surfaceVariant = IslandColors.AppSurfaceVariant,
            onSurfaceVariant = IslandColors.TextSecondary,
            outline = IslandColors.AppOutline,
            outlineVariant = IslandColors.OutlineDark,
            error = IslandColors.AccentRed,
            onError = IslandColors.TextOnAccent,
            surfaceContainer = IslandColors.AppSurface,
            surfaceContainerHigh = IslandColors.AppSurfaceVariant,
            inverseSurface = IslandColors.TextPrimary,
            inverseOnSurface = IslandColors.AppBackground,
        )
    }

    CompositionLocalProvider(
        LocalIslandSurface provides IslandSurfaceTokens(appearance = appearance, reduceMotion = reduceMotion),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = IslandAppTypography,
            shapes = IslandAppShapes,
            content = content,
        )
    }
}
