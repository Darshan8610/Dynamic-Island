package com.dynamicisland.host

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.dynamicisland.engine.service.DynamicIslandService
import com.dynamicisland.engine.settings.BehaviorPreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class IslandUiTest {
    @get:Rule val compose=createAndroidComposeRule<VisualTestActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun shell(command:String):String = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use {it.readText()}
    }
    @Before fun unlockTestDevice() {
        UiDevice.getInstance(instrumentation).wakeUp()
        shell("wm dismiss-keyguard")
        compose.runOnUiThread {
            BehaviorPreferences.get(compose.activity).update {
                it.copy(interactionMode = com.dynamicisland.core.geometry.InteractionMode.ANDROID_CLASSIC, reducedMotion = true)
            }
        }
    }

    @Test fun staleTelemetryKeepsOpaqueBlackCapsule() {
        compose.runOnUiThread { compose.activity.scenario = "Music"; compose.activity.stale = true }
        compose.waitForIdle()
        val bounds = compose.onNodeWithContentDescription("Primary island. Open controls", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(Color.BLACK, bitmap.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt()))
    }
    @Test fun capturesAllCapsuleStatesWithoutChangingFootprint() {
        val cases=listOf("Idle","Music","Incoming call","Active call","Timer","Stopwatch","Navigation","Charging","Low battery","Bluetooth","Notification","Two activities")
        val folder=File(compose.activity.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
        var expectedWidth=0f;var expectedHeight=0f
        cases.forEach { name ->
            compose.runOnUiThread {compose.activity.scenario=name}
            compose.waitForIdle()
            val node=compose.onNodeWithContentDescription("Primary island. Open controls",useUnmergedTree=true)
            val bounds=node.fetchSemanticsNode().boundsInRoot
            if(expectedWidth==0f){expectedWidth=bounds.width;expectedHeight=bounds.height}
            assertEquals(expectedWidth,bounds.width,0.5f);assertEquals(expectedHeight,bounds.height,0.5f)
            val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
            assertEquals(Color.BLACK,bitmap.getPixel(bounds.center.x.toInt(),bounds.center.y.toInt()))
            File(folder,name.replace(' ','_')+".png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    @Test fun entireCompactCapsuleOpensControlsIncludingTheMusicIndicator() {
        compose.runOnUiThread {compose.activity.scenario="Music"}
        compose.onNodeWithContentDescription("Primary island. Open controls", useUnmergedTree=true)
            .performTouchInput { click(androidx.compose.ui.geometry.Offset(width - 12f, height / 2f)) }
        compose.runOnIdle {assertEquals(0,compose.activity.plays);assertEquals(1,compose.activity.taps)}
    }
    @Test fun boundedWindowPassesOutsideTouchesAndStopsOnRevocation() {
        val context=compose.activity
        try {
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            compose.runOnUiThread {DynamicIslandService.start(context)}
            compose.waitUntil(15000) {DynamicIslandService.isRunning}
            // The overlay sits near the top; the debug target remains touchable below it.
            val device=UiDevice.getInstance(instrumentation)
            val target = device.findObject(androidx.test.uiautomator.By.text("Underlying touch target"))
            assertNotNull(target);target.click()
            compose.runOnIdle { assertEquals(1,compose.activity.outsideTaps) }
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW deny")
            compose.waitUntil(10000) {!DynamicIslandService.isRunning}
            assertFalse(BehaviorPreferences.get(context).options.value.enabled && DynamicIslandService.isRunning)
        } finally {
            compose.runOnUiThread {DynamicIslandService.stop(context)}
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW default")
        }
    }
    @Test fun realOverlayExpandsControlsMediaAndCollapsesOutside() {
        val context = compose.activity
        val device = UiDevice.getInstance(instrumentation)
        val listener = "${context.packageName}/com.dynamicisland.engine.ingestion.notifications.DynamicIslandNotificationListener"
        fun <T : Any> control(description: String, action: (androidx.test.uiautomator.UiObject2) -> T): T {
            compose.waitForIdle()
            var result: T? = null
            try {
                // ComposeRule owns every ComposeView's frame clock, including service windows.
                // UIAutomator's wall-clock wait alone cannot drive their recompositions.
                compose.waitUntil(20000) {
                    try {
                        // API 35's accessibility cache can retain detached virtual nodes after
                        // a track-key change; refresh the tree before resolving an action.
                        if (android.os.Build.VERSION.SDK_INT >= 35) instrumentation.uiAutomation.clearCache()
                        val node = device.findObject(androidx.test.uiautomator.By.desc(description))
                        if (node == null) false else {
                            result = action(node)
                            true
                        }
                    } catch (_: androidx.test.uiautomator.StaleObjectException) {
                        // Track changes and window resizing replace virtual accessibility nodes.
                        false
                    }
                }
            } catch (_: ComposeTimeoutException) {
                // Preserve state/hierarchy below before cleanup removes the real overlay.
            }
            result?.let { return it }
            val options = BehaviorPreferences.get(context).options.value
            android.util.Log.e("IslandUiTest", "Missing=$description state=${DynamicIslandService.state.value} running=${DynamicIslandService.isRunning} error=${DynamicIslandService.error.value} mode=${options.interactionMode} tap=${options.tapAction}")
            val hierarchy = java.io.ByteArrayOutputStream()
            device.dumpWindowHierarchy(hierarchy)
            hierarchy.toString("UTF-8").chunked(3000).forEach { android.util.Log.e("IslandUiTest", it) }
            error("Missing overlay control: $description")
        }
        try {
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            shell("cmd notification allow_listener $listener")
            if (android.os.Build.VERSION.SDK_INT >= 33) shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            compose.runOnUiThread {
                BehaviorPreferences.get(context).update { it.copy(reducedMotion = true) }
                context.startTestMedia()
                DynamicIslandService.start(context)
            }
            compose.waitUntil(30000) { DynamicIslandService.media.value?.title?.startsWith("Test track") == true }
            // Leave the host: controls must be part of the overlay above another app.
            device.pressHome()
            val original = control("Primary island. Open controls") { it.visibleBounds }
            device.click(original.centerX(), original.centerY())
            control("Next track") { it.click() }
            compose.waitUntil(10000) { context.nextTracks == 1 }
            control("Previous track") { it.click() }
            compose.waitUntil(10000) { context.previousTracks == 1 }
            control("Pause") { it.click() }
            compose.waitUntil(10000) { context.transportPauses == 1 }
            control("Play") { it.click() }
            compose.waitUntil(10000) { context.transportPlays == 1 }
            val folder=File(context.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
            device.takeScreenshot(File(folder,"Expanded_media_overlay.png"))
            control("Collapse island") { it.click() }
            val closed = control("Primary island. Open controls") { it.visibleBounds }
            assertEquals(original, closed)
            device.click(closed.centerX(), closed.centerY())
            control("Collapse island") { it.isEnabled }
            device.click(device.displayWidth/2, device.displayHeight*3/4)
            control("Primary island. Open controls") { it.isEnabled }
            assertNull(device.findObject(androidx.test.uiautomator.By.desc("Collapse island")))
            // The same resolved "open controls" gesture must expand in Apple parity mode.
            compose.runOnUiThread {
                BehaviorPreferences.get(context).update {
                    it.copy(interactionMode = com.dynamicisland.core.geometry.InteractionMode.APPLE_PARITY)
                }
            }
            device.waitForIdle()
            compose.waitForIdle()
            // Inject the hold with Compose's event clock; a wall-clock-only gesture can
            // release before the test clock reaches the pointer-input long-press timeout.
            compose.onNode(
                hasContentDescription("Primary island. Open controls") and
                    SemanticsMatcher("capsule at the service window origin") { kotlin.math.abs(it.boundsInRoot.top) < 1f },
                useUnmergedTree = true
            ).performTouchInput { longClick(durationMillis = 1000L) }
            control("Next track") { it.isEnabled }
            control("Collapse island") { it.click() }
            control("Primary island. Open controls") { it.isEnabled }
            // Stop must remove the real window even after multiple open/close cycles.
            compose.runOnUiThread { DynamicIslandService.stop(context) }
            compose.waitUntil(10000) { !DynamicIslandService.isRunning }
        } finally {
            compose.runOnUiThread { DynamicIslandService.stop(context) }
            shell("cmd notification disallow_listener $listener")
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW default")
            device.pressHome()
        }
    }
    @Test fun rotationAndLargeFontsKeepCapsuleOnScreen() {
        val device=UiDevice.getInstance(instrumentation)
        try {
            device.setOrientationLeft();device.waitForIdle()
            compose.onNodeWithContentDescription("Primary island. Open controls",useUnmergedTree=true).assertIsDisplayed()
            shell("settings put system font_scale 1.5")
            device.waitForIdle()
            compose.onNodeWithContentDescription("Primary island. Open controls",useUnmergedTree=true).assertIsDisplayed()
        } finally {shell("settings put system font_scale 1.0");device.setOrientationNatural();device.unfreezeRotation()}
    }
}
