package dev.island.layout

import dev.island.TestEvents
import dev.island.core.platform.CutoutKind
import dev.island.domain.model.IslandAppearance
import dev.island.domain.model.IslandEventType
import dev.island.domain.model.IslandPosition
import dev.island.domain.model.IslandPreset
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.LandscapeBehavior
import dev.island.feature.island.layout.IslandLayoutInput
import dev.island.feature.island.layout.IslandMetrics
import dev.island.feature.island.ui.widgets.IslandFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Geometry and formatting are the two things most likely to be wrong on a device nobody tested on,
 * so they are pinned by tests instead of by eyes.
 *
 * Every case here uses dp derived from an input — never a resolution — which is the same rule the
 * production code follows: a 360dp phone, a 428dp flagship, a tablet and a foldable all get valid,
 * different numbers from the same functions.
 */
class IslandMetricsTest {

    private fun phoneInput(
        widthDp: Float = 412f,
        heightDp: Float = 915f,
        landscape: Boolean = false,
        appearance: IslandAppearance = IslandAppearance(),
        position: IslandPosition = IslandPosition.AUTO_CUTOUT,
        landscapeBehavior: LandscapeBehavior = LandscapeBehavior.ADAPT,
        cutoutKind: CutoutKind = CutoutKind.CENTER_PUNCH_HOLE,
        isLargeScreen: Boolean = false,
        fontScale: Float = 1f,
    ) = IslandLayoutInput(
        screenWidthDp = widthDp,
        screenHeightDp = heightDp,
        safeTopDp = 32f,
        statusBarHeightDp = 24f,
        cutoutKind = cutoutKind,
        hasCutout = cutoutKind != CutoutKind.NONE,
        cutoutTopDp = 8f,
        cutoutBottomDp = 32f,
        cutoutLeftDp = widthDp / 2f - 8f,
        cutoutRightDp = widthDp / 2f + 8f,
        landscape = landscape,
        isLargeScreen = isLargeScreen,
        fontScale = fontScale,
        position = position,
        landscapeBehavior = landscapeBehavior,
        appearance = appearance,
    )

    @Test
    fun `collapsed pill stays inside the design band`() {
        val bounds = IslandMetrics.collapsed(phoneInput())

        assertTrue(bounds.visible)
        assertFalse(bounds.minimized)
        assertTrue("width ${bounds.widthDp}", bounds.widthDp >= IslandMetrics.MIN_COLLAPSED_WIDTH_DP)
        assertTrue("width ${bounds.widthDp}", bounds.widthDp <= IslandMetrics.MAX_COLLAPSED_WIDTH_DP)
        assertTrue("height ${bounds.heightDp}", bounds.heightDp >= IslandMetrics.MIN_COLLAPSED_HEIGHT_DP)
        assertTrue("height ${bounds.heightDp}", bounds.heightDp <= IslandMetrics.MAX_COLLAPSED_HEIGHT_DP)
    }

    @Test
    fun `collapsed pill is a perfect capsule`() {
        val bounds = IslandMetrics.collapsed(phoneInput())
        assertEquals(bounds.heightDp / 2f, bounds.cornerRadiusDp, 0.5f)
    }

    @Test
    fun `pill is centred and never under the status bar`() {
        val input = phoneInput()
        val bounds = IslandMetrics.collapsed(input)

        val centreOffset = bounds.xDp + bounds.widthDp / 2f - input.screenWidthDp / 2f
        assertTrue("pill must be centred, offset was $centreOffset", kotlin.math.abs(centreOffset) <= 8f)
        assertTrue("pill must clear the status bar", bounds.yDp >= 0f)
    }

    @Test
    fun `a punch hole is covered, not avoided`() {
        val input = phoneInput(cutoutKind = CutoutKind.CENTER_PUNCH_HOLE)
        val bounds = IslandMetrics.collapsed(input)

        val cutoutCentreY = (input.cutoutTopDp + input.cutoutBottomDp) / 2f
        val pillCentreY = bounds.yDp + bounds.heightDp / 2f
        assertTrue(
            "the pill should sit over the camera cutout (pill=$pillCentreY cutout=$cutoutCentreY)",
            kotlin.math.abs(pillCentreY - cutoutCentreY) <= bounds.heightDp,
        )
    }

    @Test
    fun `expanded is wider and taller than collapsed, and still inside the screen`() {
        val input = phoneInput()
        val collapsed = IslandMetrics.collapsed(input)
        val expanded = IslandMetrics.expanded(input, IslandEventType.MEDIA)

        assertTrue(expanded.widthDp > collapsed.widthDp)
        assertTrue(expanded.heightDp > collapsed.heightDp)
        assertTrue(expanded.widthDp <= input.screenWidthDp)
        assertTrue(expanded.xDp >= 0f)
        assertTrue(expanded.yDp >= 0f)
    }

    @Test
    fun `expanded width is capped on large screens`() {
        val tablet = IslandMetrics.expanded(phoneInput(widthDp = 900f, isLargeScreen = true), IslandEventType.MEDIA)
        assertTrue(tablet.widthDp <= IslandMetrics.MAX_EXPANDED_WIDTH_DP)
    }

    @Test
    fun `per event heights differ so each renderer gets the space it needs`() {
        val appearance = IslandAppearance()
        val media = IslandMetrics.expandedHeightDp(IslandEventType.MEDIA, appearance)
        val bluetooth = IslandMetrics.expandedHeightDp(IslandEventType.BLUETOOTH, appearance)

        assertTrue("media needs more room than a device pill", media > bluetooth)
        assertTrue(IslandMetrics.expandedHeightDp(null, appearance) > 0f)
    }

    @Test
    fun `user size scale changes the pill but keeps it usable`() {
        val small = IslandMetrics.collapsed(phoneInput(appearance = IslandAppearance(sizeScale = 0.7f)))
        val large = IslandMetrics.collapsed(phoneInput(appearance = IslandAppearance(sizeScale = 1.5f)))

        assertTrue(large.widthDp > small.widthDp)
        assertTrue(small.heightDp >= IslandMetrics.MIN_COLLAPSED_HEIGHT_DP)
        assertTrue(large.widthDp <= IslandMetrics.MAX_COLLAPSED_WIDTH_DP)
    }

    @Test
    fun `a large system font scale widens the pill instead of clipping text`() {
        val normal = IslandMetrics.collapsed(phoneInput())
        val largeFont = IslandMetrics.collapsed(phoneInput(fontScale = 1.3f))

        assertTrue(largeFont.widthDp >= normal.widthDp)
    }

    @Test
    fun `landscape can hide or minimise the island`() {
        val hidden = IslandMetrics.collapsed(
            phoneInput(landscape = true, landscapeBehavior = LandscapeBehavior.HIDE),
        )
        assertFalse(hidden.visible)

        val minimized = IslandMetrics.collapsed(
            phoneInput(landscape = true, landscapeBehavior = LandscapeBehavior.MINIMIZE),
        )
        assertTrue(minimized.minimized)
        assertTrue(minimized.widthDp <= IslandMetrics.collapsed(phoneInput()).widthDp)
    }

    @Test
    fun `landscape adapt keeps a usable, narrower pill`() {
        val bounds = IslandMetrics.collapsed(
            phoneInput(widthDp = 915f, heightDp = 412f, landscape = true),
        )
        assertTrue(bounds.visible)
        assertFalse(bounds.minimized)
        assertTrue(bounds.widthDp <= 915f / 2f)
    }

    @Test
    fun `a very narrow screen cannot overflow`() {
        val bounds = IslandMetrics.collapsed(phoneInput(widthDp = 240f))
        assertTrue(bounds.widthDp <= 240f)
        assertTrue(bounds.xDp >= 0f)
        assertTrue(bounds.xDp + bounds.widthDp <= 240f + 0.5f)
    }

    @Test
    fun `custom position honours the user's vertical offset`() {
        val base = IslandMetrics.collapsed(phoneInput(position = IslandPosition.TOP_CENTER))
        val lowered = IslandMetrics.collapsed(
            phoneInput(
                position = IslandPosition.SLIGHTLY_LOWER,
            ),
        )
        assertTrue(lowered.yDp > base.yDp)
    }
}

class IslandFormatTest {

    @Test
    fun `clock formats minutes and seconds and rolls over to hours`() {
        assertEquals("00:00", IslandFormat.clock(0L))
        assertEquals("00:01", IslandFormat.clock(1L))
        assertEquals("00:10", IslandFormat.clock(9_600L))
        assertEquals("01:00", IslandFormat.clock(59_500L))
        assertEquals("10:00", IslandFormat.clock(600_000L))
        assertEquals("1:00:00", IslandFormat.clock(3_600_000L))
    }

    @Test
    fun `clock never shows a negative countdown`() {
        assertEquals("00:00", IslandFormat.clock(-5_000L))
    }

    @Test
    fun `stopwatch formatting keeps tenths`() {
        assertEquals("00:01.5", IslandFormat.clockWithTenths(1_500L))
        assertEquals("01:35.4", IslandFormat.clockWithTenths(95_400L))
    }

    @Test
    fun `percent is clamped`() {
        assertEquals("0%", IslandFormat.percent(-12))
        assertEquals("64%", IslandFormat.percent(64))
        assertEquals("100%", IslandFormat.percent(150))
    }

    @Test
    fun `power and temperature read like the charger reports them`() {
        assertEquals("22.5W", IslandFormat.wattage(22.5f))
        assertNull(IslandFormat.wattage(0f))
        assertNull(IslandFormat.wattage(null))
        assertEquals("31.2°C", IslandFormat.temperature(312))
        assertEquals("9.00V", IslandFormat.voltage(9_000))
    }

    @Test
    fun `byte sizes step through the units`() {
        assertEquals("512 B", IslandFormat.bytes(512L))
        assertEquals("1 KB", IslandFormat.bytes(1024L))
        assertEquals("2 MB", IslandFormat.bytes(2L * 1024 * 1024))
        assertEquals("1.5 GB", IslandFormat.bytes((1.5 * 1024 * 1024 * 1024).toLong()))
        assertNull(IslandFormat.bytes(null))
    }

    @Test
    fun `navigation distances switch units at a kilometre`() {
        assertEquals("350 m", IslandFormat.distance(352))
        assertEquals("1.2 km", IslandFormat.distance(1_200))
        assertNull(IslandFormat.distance(null))
    }

    @Test
    fun `eta stays readable past an hour`() {
        assertEquals("12 min", IslandFormat.eta(12))
        assertEquals("1h 15m", IslandFormat.eta(75))
        assertNull(IslandFormat.eta(null))
    }

    @Test
    fun `group counts only render for real groups`() {
        assertNull(IslandFormat.groupCount(current = 1, total = 1))
        assertEquals("2/4", IslandFormat.groupCount(current = 2, total = 4))
    }
}

class IslandPresetTest {

    @Test
    fun `custom keeps whatever the user already chose`() {
        val base = IslandAppearance(sizeScale = 1.2f, cornerRadiusDp = 30f, opacity = 0.8f)
        assertEquals(base, IslandPreset.CUSTOM.apply(base))
    }

    @Test
    fun `presets actually differ from each other`() {
        val base = IslandAppearance()
        val minimal = IslandPreset.MINIMAL.apply(base)
        val large = IslandPreset.LARGE.apply(base)

        assertTrue(minimal != large)
        assertTrue(minimal != base || large != base)
    }

    @Test
    fun `presets keep values inside the supported ranges`() {
        IslandPreset.entries.forEach { preset ->
            val appearance = preset.apply(IslandAppearance())
            assertTrue(
                "$preset sizeScale ${appearance.sizeScale}",
                appearance.sizeScale in IslandAppearance.MIN_SIZE_SCALE..IslandAppearance.MAX_SIZE_SCALE,
            )
            assertTrue(
                "$preset animationSpeed ${appearance.animationSpeed}",
                appearance.animationSpeed in IslandAppearance.MIN_ANIMATION_SPEED..IslandAppearance.MAX_ANIMATION_SPEED,
            )
            assertTrue("$preset opacity ${appearance.opacity}", appearance.opacity in 0.3f..1f)
        }
    }
}

class TestEventsSanityTest {

    @Test
    fun `fixtures are usable by every test without Android`() {
        val notification = TestEvents.notification()
        assertEquals(IslandEventType.NOTIFICATION, notification.type)
        assertEquals("com.example.chat", notification.sourcePackage)

        val media = TestEvents.media()
        assertTrue(media.persistent)
        assertEquals(224_000L, media.media.durationMs)
        assertEquals(0.34f, media.media.fractionAt(0L) ?: 0f, 0.02f)

        val settings = IslandSettings(islandEnabled = true)
        assertTrue(settings.islandEnabled)
    }
}
