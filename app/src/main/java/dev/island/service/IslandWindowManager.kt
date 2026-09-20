package dev.island.service

import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import dev.island.core.logging.IslandLogger

/** Window geometry in pixels, computed by [dev.island.feature.island.layout.IslandMetrics]. */
data class WindowBoundsPx(
    val widthPx: Int,
    val heightPx: Int,
    val xPx: Int,
    val yPx: Int,
)

/**
 * Every `WindowManager` call in the app lives here (spec: never scatter addView across the project).
 *
 * Window behaviour:
 * - `TYPE_APPLICATION_OVERLAY` — the documented type for windows above other apps, which requires
 *   the user-granted SYSTEM_ALERT_WINDOW permission;
 * - `FLAG_NOT_FOCUSABLE` — never steals focus, never affects the IME, never blocks back/home;
 * - `FLAG_NOT_TOUCH_MODAL` — touches outside the window go to the app underneath;
 * - `FLAG_LAYOUT_IN_SCREEN` + `FLAG_LAYOUT_NO_LIMITS` — lets the pill sit in the status-bar /
 *   cutout area instead of being pushed below it;
 * - no dim, no blur of the app below, no full-screen touch layer: the window is exactly the size
 *   of the island, so the underlying app keeps receiving every touch outside the pill.
 */
class IslandWindowManager(
    private val context: android.content.Context,
    private val logger: IslandLogger,
) {

    private val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as? WindowManager
    private var attachedView: View? = null
    private var currentParams: WindowManager.LayoutParams? = null

    var isAttached: Boolean = false
        private set

    /** Last failure, surfaced on the diagnostics screen instead of crashing the service. */
    var lastError: String? = null
        private set

    fun canDrawOverlays(): Boolean = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    /** Attaches the island host view. Returns false (never throws) when the window cannot be added. */
    fun attach(view: View, bounds: WindowBoundsPx): Boolean {
        if (windowManager == null) {
            lastError = "WindowManager unavailable"
            logger.e(TAG, lastError!!)
            return false
        }
        if (!canDrawOverlays()) {
            lastError = "SYSTEM_ALERT_WINDOW not granted"
            logger.w(TAG, lastError!!)
            return false
        }
        if (isAttached) {
            updateBounds(bounds)
            return true
        }
        val params = buildLayoutParams(bounds)
        val manager = windowManager ?: return false
        return runCatching {
            manager.addView(view, params)
            attachedView = view
            currentParams = params
            isAttached = true
            lastError = null
            logger.i(TAG, "overlay attached ${bounds.widthPx}x${bounds.heightPx} @(${bounds.xPx},${bounds.yPx})")
            true
        }.getOrElse {
            lastError = "${it.javaClass.simpleName}: ${it.message}"
            logger.e(TAG, "overlay attach failed", it)
            false
        }
    }

    /** Resizes/repositions the window. Called on phase changes, rotation, fold and cutout changes. */
    fun updateBounds(bounds: WindowBoundsPx) {
        val view = attachedView ?: return
        val params = currentParams ?: return
        if (params.width == bounds.widthPx && params.height == bounds.heightPx &&
            params.x == bounds.xPx && params.y == bounds.yPx
        ) {
            return
        }
        params.width = bounds.widthPx.coerceAtLeast(1)
        params.height = bounds.heightPx.coerceAtLeast(1)
        params.x = bounds.xPx.coerceAtLeast(0)
        params.y = bounds.yPx.coerceAtLeast(0)
        runCatching { windowManager?.updateViewLayout(view, params) }
            .onFailure {
                lastError = "${it.javaClass.simpleName}: ${it.message}"
                logger.w(TAG, "overlay update failed; re-attaching", it)
                reattach(view, bounds)
            }
    }

    /** Shows or hides the window without removing it (screen off, lock screen, landscape hide). */
    fun setVisible(visible: Boolean) {
        val view = attachedView ?: return
        val target = if (visible) View.VISIBLE else View.INVISIBLE
        if (view.visibility == target) return
        view.visibility = target
        logger.d(TAG, "overlay visibility=$visible")
    }

    fun detach() {
        val view = attachedView ?: return
        runCatching { windowManager?.removeViewImmediate(view) }
            .onFailure { logger.w(TAG, "overlay detach failed", it) }
        attachedView = null
        currentParams = null
        isAttached = false
        logger.i(TAG, "overlay detached")
    }

    private fun reattach(view: View, bounds: WindowBoundsPx) {
        detach()
        attach(view, bounds)
    }

    private fun buildLayoutParams(bounds: WindowBoundsPx): WindowManager.LayoutParams =
        WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            width = bounds.widthPx.coerceAtLeast(1)
            height = bounds.heightPx.coerceAtLeast(1)
            x = bounds.xPx.coerceAtLeast(0)
            y = bounds.yPx.coerceAtLeast(0)
            gravity = Gravity.TOP or Gravity.START
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            // No dim behind the island, no blur of the underlying app, no screen-off optimisation.
            dimAmount = 0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            windowAnimations = 0 // Compose owns every animation; the window must not add its own.
        }

    companion object {
        private const val TAG = "IslandWindow"
    }
}
