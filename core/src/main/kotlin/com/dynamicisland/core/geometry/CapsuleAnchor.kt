package com.dynamicisland.core.geometry

import kotlin.math.roundToInt

/** Pixel-only, state-independent placement for one bounded overlay window. */
object CapsuleAnchor {
    const val WIDTH_DP = 130f
    const val HEIGHT_DP = 38f
    data class Window(val left: Int, val top: Int, val width: Int, val height: Int, val rotation: Float)

    fun resolve(
        screenWidth: Int, screenHeight: Int, density: Float, cutout: PixelRect?,
        widthScale: Float = 1f, heightScale: Float = 1f,
        offsetX: Float = 0f, offsetY: Float = 0f,
        statusBarTop: Int = 0, clearSystemBars: Boolean = true,
        safeLeft: Int = 0, safeRight: Int = 0
    ): Window {
        require(screenWidth > 0 && screenHeight > 0)
        require(density.isFinite() && density > 0)
        fun safeScale(n: Float) = if (n.isFinite()) n.coerceIn(.5f, 1.4f) else 1f
        val validCutout = cutout?.takeUnless { it.isEmpty }
        val edge = validCutout?.let {
            listOf(it.top, it.left, screenWidth - it.right, screenHeight - it.bottom)
                .withIndex().minBy { entry -> entry.value }.index
        } ?: 0
        val rotation = when (edge) { 1 -> 90f; 2 -> -90f; 3 -> 180f; else -> 0f }
        val long = (WIDTH_DP * safeScale(widthScale) * density).roundToInt().coerceAtLeast(1)
        val short = (HEIGHT_DP * safeScale(heightScale) * density).roundToInt().coerceAtLeast(1)
        val side = edge == 1 || edge == 2
        val w = (if (side) short else long).coerceAtMost(screenWidth)
        val h = (if (side) long else short).coerceAtMost(screenHeight)
        val gap = (4 * density).roundToInt()
        var cx = validCutout?.centerX ?: screenWidth / 2f
        var cy = validCutout?.centerY ?: (statusBarTop + gap + h / 2f)
        if (clearSystemBars) {
            when (edge) {
                0 -> cy = maxOf(cy, statusBarTop + gap + h / 2f)
                1 -> cx = maxOf(cx, safeLeft + gap + w / 2f)
                2 -> cx = minOf(cx, screenWidth - safeRight - gap - w / 2f)
            }
        }
        fun safeOffset(n: Float) = if (n.isFinite()) n * density else 0f
        return Window(
            (cx - w / 2f + safeOffset(offsetX)).roundToInt().coerceIn(0, screenWidth - w),
            (cy - h / 2f + safeOffset(offsetY)).roundToInt().coerceIn(0, screenHeight - h),
            w, h, rotation
        )
    }
}
