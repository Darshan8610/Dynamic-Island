package dev.island.core.platform

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.os.Build
import android.view.WindowManager

/** Live display geometry. Never cached: foldables and rotation change it at any time. */
data class DisplayInfo(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val widthDp: Float,
    val heightDp: Float,
    val fontScale: Float,
    val landscape: Boolean,
    val smallestScreenWidthDp: Int,
    val isLargeScreen: Boolean,
    val isRound: Boolean,
)

/**
 * Reads display geometry from the context the island is actually attached to (a service context
 * sees the same configuration as the default display, and updates on rotation/fold).
 */
class DisplayInfoProvider(private val context: Context) {

    fun current(): DisplayInfo {
        val resources = context.resources
        val metrics = resources.displayMetrics
        val configuration = resources.configuration
        val size = displaySize()

        val widthPx = if (size.x > 0) size.x else metrics.widthPixels
        val heightPx = if (size.y > 0) size.y else metrics.heightPixels
        val density = if (metrics.density > 0f) metrics.density else 1f

        return DisplayInfo(
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            widthDp = widthPx / density,
            heightDp = heightPx / density,
            fontScale = configuration.fontScale.takeIf { it > 0f } ?: 1f,
            landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
            smallestScreenWidthDp = configuration.smallestScreenWidthDp,
            isLargeScreen = configuration.smallestScreenWidthDp >= LARGE_SCREEN_SMALLEST_WIDTH_DP ||
                (widthPx / density) >= LARGE_SCREEN_WIDTH_DP,
            isRound = configuration.isScreenRound,
        )
    }

    private fun displaySize(): Point {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return Point(0, 0)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = windowManager.currentWindowMetrics.bounds
                Point(bounds.width(), bounds.height())
            } else {
                val point = Point()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealSize(point)
                point
            }
        }.getOrDefault(Point(0, 0))
    }

    companion object {
        const val LARGE_SCREEN_SMALLEST_WIDTH_DP = 600
        const val LARGE_SCREEN_WIDTH_DP = 700f
    }
}
