package dev.island.layout

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.island.core.platform.DisplayInfoProvider
import dev.island.domain.model.IslandAppearance
import dev.island.domain.model.IslandEventType
import dev.island.domain.model.IslandPosition
import dev.island.feature.island.layout.IslandLayoutInput
import dev.island.feature.island.layout.IslandMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The geometry rules, checked against the *actual* device the tests run on.
 *
 * [dev.island.layout.IslandMetricsTest] covers the design band with synthetic inputs; this covers the
 * half that only a real device can answer: does the derived pill actually fit the screen in front of
 * us, at this density, with this font scale, in this orientation, with a real status bar inset? The
 * app never hard-codes a resolution, so this is the test that proves it.
 */
@RunWith(AndroidJUnit4::class)
class IslandGeometryOnDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val display = DisplayInfoProvider(context).current()

    private fun input(
        sizeScale: Float = 1f,
        fontScale: Float = display.fontScale,
        position: IslandPosition = IslandPosition.AUTO_CUTOUT,
        landscape: Boolean = display.landscape,
    ) = IslandLayoutInput(
        screenWidthDp = display.widthDp,
        screenHeightDp = display.heightDp,
        safeTopDp = statusBarHeightDp(),
        statusBarHeightDp = statusBarHeightDp(),
        landscape = landscape,
        isLargeScreen = display.isLargeScreen,
        fontScale = fontScale,
        position = position,
        appearance = IslandAppearance(sizeScale = sizeScale),
    )

    private fun statusBarHeightDp(): Float {
        val resources = context.resources
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        val px = if (id > 0) resources.getDimensionPixelSize(id) else 0
        return if (px > 0) px / display.density else 24f
    }

    @Test
    fun theDeviceReportsAUsableDisplay() {
        Log.i(TAG, "device display: ${display.widthDp}x${display.heightDp}dp @${display.density} " +
            "fontScale=${display.fontScale} landscape=${display.landscape} large=${display.isLargeScreen}")
        assertTrue("widthDp was ${display.widthDp}", display.widthDp > 100f)
        assertTrue("heightDp was ${display.heightDp}", display.heightDp > 100f)
        assertTrue("density was ${display.density}", display.density > 0f)
    }

    @Test
    fun theCollapsedPillFitsThisScreenAtEveryUserScale() {
        listOf(0.7f, 0.85f, 1f, 1.25f, 1.5f).forEach { scale ->
            val bounds = IslandMetrics.collapsed(input(sizeScale = scale))
            assertTrue("scale $scale: width ${bounds.widthDp}", bounds.widthDp > 0f)
            assertTrue("scale $scale: x ${bounds.xDp}", bounds.xDp >= 0f)
            assertTrue(
                "scale $scale: pill ${bounds.xDp}+${bounds.widthDp} overflows ${display.widthDp}",
                bounds.xDp + bounds.widthDp <= display.widthDp + 0.5f,
            )
            assertTrue(
                "scale $scale: pill top ${bounds.yDp} is above the screen",
                bounds.yDp >= 0f,
            )
            assertEquals("scale $scale: pill is not a capsule", bounds.heightDp / 2f, bounds.cornerRadiusDp, 1f)
        }
    }

    @Test
    fun theExpandedCardFitsThisScreen() {
        IslandEventType.entries.forEach { type ->
            val bounds = IslandMetrics.expanded(input(), type)
            assertTrue(
                "$type: card ${bounds.widthDp}dp wider than the ${display.widthDp}dp screen",
                bounds.widthDp <= display.widthDp + 0.5f,
            )
            assertTrue(
                "$type: card ${bounds.heightDp}dp taller than the ${display.heightDp}dp screen",
                bounds.heightDp <= display.heightDp + 0.5f,
            )
            assertTrue("$type: x ${bounds.xDp}", bounds.xDp >= 0f)
            assertTrue("$type: y ${bounds.yDp}", bounds.yDp >= 0f)
        }
    }

    @Test
    fun aLargeSystemFontScaleStillFits() {
        val bounds = IslandMetrics.collapsed(input(fontScale = 1.45f))
        assertTrue(
            "font scale 1.45 pushed the pill to ${bounds.xDp}+${bounds.widthDp} on a ${display.widthDp}dp screen",
            bounds.xDp + bounds.widthDp <= display.widthDp + 0.5f,
        )
    }

    @Test
    fun landscapeLayoutsStayInsideTheRotatedScreen() {
        val width = if (display.landscape) display.widthDp else display.heightDp
        val height = if (display.landscape) display.heightDp else display.widthDp

        val adapted = IslandMetrics.collapsed(input(landscape = true).copy(screenWidthDp = width, screenHeightDp = height))
        if (adapted.visible) {
            assertTrue(
                "landscape pill ${adapted.xDp}+${adapted.widthDp} overflows ${width}dp",
                adapted.xDp + adapted.widthDp <= width + 0.5f,
            )
            assertTrue("landscape pill is taller than the screen", adapted.heightDp <= height + 0.5f)
        }

        val hidden = IslandMetrics.collapsed(
            input(landscape = true)
                .copy(
                    screenWidthDp = width,
                    screenHeightDp = height,
                    landscapeBehavior = dev.island.domain.model.LandscapeBehavior.HIDE,
                ),
        )
        assertTrue("HIDE must not draw anything", !hidden.visible)
    }

    private companion object {
        const val TAG = "IslandGeometry"
    }
}
