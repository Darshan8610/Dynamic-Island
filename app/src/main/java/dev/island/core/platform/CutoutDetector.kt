package dev.island.core.platform

import android.graphics.Rect
import android.os.Build
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.island.core.logging.IslandLogger

enum class CutoutKind {
    NONE,
    CENTER_PUNCH_HOLE,
    LEFT_PUNCH_HOLE,
    RIGHT_PUNCH_HOLE,
    WATERDROP,
    WIDE_NOTCH,
    OTHER,
}

/**
 * Everything the island needs to place itself around the camera cutout.
 *
 * Values come from [WindowInsetsCompat.getDisplayCutout] — never from hard-coded coordinates or
 * device model tables, because OEM cutouts move between builds and foldables change geometry at
 * runtime.
 */
data class CutoutInfo(
    val kind: CutoutKind = CutoutKind.NONE,
    val hasCutout: Boolean = false,
    val safeTopPx: Int = 0,
    val statusBarHeightPx: Int = 0,
    val cutoutTopPx: Int = 0,
    val cutoutBottomPx: Int = 0,
    val cutoutCenterXPx: Int? = null,
    val cutoutWidthPx: Int = 0,
    val cutoutHeightPx: Int = 0,
    val boundsPx: List<Rect> = emptyList(),
    val screenWidthPx: Int = 0,
    val screenHeightPx: Int = 0,
) {
    val isCentered: Boolean
        get() {
            val center = cutoutCenterXPx ?: return false
            if (screenWidthPx <= 0) return false
            return kotlin.math.abs(center - screenWidthPx / 2f) < screenWidthPx * CENTER_TOLERANCE
        }

    companion object {
        const val CENTER_TOLERANCE = 0.08f
        val Unknown = CutoutInfo()
    }
}

/**
 * Reads display cutout + status bar geometry from a live view's window insets.
 *
 * On API 26/27 there is no display-cutout API, so the detector falls back to the status bar inset
 * and reports [CutoutKind.NONE]; the island then sits just below the status bar, which is the
 * correct behaviour on those devices.
 */
class CutoutDetector(private val logger: IslandLogger) {

    fun detect(view: View): CutoutInfo {
        val rootInsets = runCatching { ViewCompat.getRootWindowInsets(view) }
            .onFailure { logger.w(TAG, "root insets unavailable", it) }
            .getOrNull()
        return fromInsets(rootInsets, view.width.takeIf { it > 0 } ?: view.resources.displayMetrics.widthPixels,
            view.height.takeIf { it > 0 } ?: view.resources.displayMetrics.heightPixels)
    }

    fun fromInsets(insets: WindowInsetsCompat?, screenWidthPx: Int, screenHeightPx: Int): CutoutInfo {
        if (insets == null) return CutoutInfo.Unknown.copy(screenWidthPx = screenWidthPx, screenHeightPx = screenHeightPx)

        val systemBars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        val safeTop = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()).top
            .coerceAtLeast(statusBarHeight)

        val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) insets.displayCutout else null
        val bounds: List<Rect> = cutout?.boundingRects.orEmpty()
        if (bounds.isEmpty()) {
            return CutoutInfo(
                kind = CutoutKind.NONE,
                hasCutout = false,
                safeTopPx = safeTop,
                statusBarHeightPx = statusBarHeight,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
            )
        }

        // Union of all cutout rects (a wide notch can be reported as several rects).
        val union = Rect(bounds.first())
        bounds.drop(1).forEach { union.union(it) }
        val width = union.width()
        val height = union.height()
        val centerX = union.centerX()

        val kind = classify(union, width, height, screenWidthPx, statusBarHeight)

        return CutoutInfo(
            kind = kind,
            hasCutout = true,
            safeTopPx = safeTop,
            statusBarHeightPx = statusBarHeight,
            cutoutTopPx = union.top,
            cutoutBottomPx = union.bottom,
            cutoutCenterXPx = centerX,
            cutoutWidthPx = width,
            cutoutHeightPx = height,
            boundsPx = bounds,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
    }

    private fun classify(union: Rect, width: Int, height: Int, screenWidthPx: Int, statusBarHeight: Int): CutoutKind {
        if (screenWidthPx <= 0) return CutoutKind.OTHER
        val relativeWidth = width.toFloat() / screenWidthPx.toFloat()
        val centered = kotlin.math.abs(union.centerX() - screenWidthPx / 2f) < screenWidthPx * CutoutInfo.CENTER_TOLERANCE

        return when {
            relativeWidth > WIDE_NOTCH_RATIO -> CutoutKind.WIDE_NOTCH
            // A punch hole is small and roughly square; a waterdrop notch is wider than tall.
            height > 0 && width.toFloat() / height.toFloat() <= PUNCH_HOLE_ASPECT && relativeWidth < PUNCH_HOLE_RATIO ->
                when {
                    centered -> CutoutKind.CENTER_PUNCH_HOLE
                    union.centerX() < screenWidthPx / 2 -> CutoutKind.LEFT_PUNCH_HOLE
                    else -> CutoutKind.RIGHT_PUNCH_HOLE
                }

            union.top <= 0 && height <= statusBarHeight * WATERDROP_HEIGHT_FACTOR -> CutoutKind.WATERDROP
            centered -> CutoutKind.WIDE_NOTCH
            else -> CutoutKind.OTHER
        }
    }

    companion object {
        private const val TAG = "CutoutDetector"
        private const val WIDE_NOTCH_RATIO = 0.30f
        private const val PUNCH_HOLE_RATIO = 0.12f
        private const val PUNCH_HOLE_ASPECT = 1.6f
        private const val WATERDROP_HEIGHT_FACTOR = 1.2f
    }
}
