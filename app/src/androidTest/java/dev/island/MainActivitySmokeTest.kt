package dev.island

import android.util.Log
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.island.core.AppGraph
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launch the real activity on a real device and prove it comes up.
 *
 * This exercises the path no unit test can: [IslandApplication.onCreate] building the object graph,
 * DataStore reading settings for the first time, the permission repository querying the platform,
 * edge-to-edge layout, and Compose inflating inside an Activity. A crash anywhere in that chain —
 * a missing resource, an uninitialised graph member, a bad window flag — fails here.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun theAppLaunchesAndRendersItsFirstScreen() {
        rule.waitForIdle()
        rule.onRoot(useUnmergedTree = true).assertExists()

        // A fresh install lands on onboarding ("Meet Island."); a returning one on the dashboard
        // ("Island"). Either way the app's own name is on screen.
        val matches = rule.onAllNodesWithText("Island", substring = true, ignoreCase = true)
            .fetchSemanticsNodes()
        Log.i(TAG, "launched: ${matches.size} node(s) mention the app name; " +
            "ready=${AppGraph.isReady()} service=${AppGraph.serviceRunning.value} " +
            "notificationAccess=${AppGraph.notificationAccess.value}")
        assertTrue("the first screen rendered no text at all", matches.isNotEmpty())
    }

    @Test
    fun theObjectGraphIsUsableAfterLaunch() {
        rule.waitForIdle()
        assertTrue("AppGraph was not initialised by the Application", AppGraph.isReady())

        // The engine must be reachable and idle-safe before the user grants anything: no overlay
        // permission on a test device means the island is disabled, not crashed.
        val state = AppGraph.engine.uiState.value
        Log.i(TAG, "engine phase=${state.phase} enabled=${state.enabled} reason=${state.disabledReason}")
        assertTrue("the engine published no state", state.phase.name.isNotEmpty())
    }

    private companion object {
        const val TAG = "IslandSmoke"
    }
}
