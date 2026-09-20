package dev.island.ui

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.island.core.AppGraph
import dev.island.domain.model.IslandSettings
import dev.island.feature.demo.DemoEvents
import dev.island.feature.island.ui.IslandPreview
import dev.island.feature.island.ui.renderers.IslandRendererRegistry
import dev.island.feature.island.ui.renderers.defaultIslandRenderers
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the *real* island — same renderers, same widgets, same tokens the overlay window uses —
 * through [IslandPreview], and asserts the content actually reaches the semantics tree.
 *
 * This is the test that catches "the renderer compiles but draws nothing": a media card with no
 * title, a notification pill with no sender, a timer with no label. Those are invisible to unit tests
 * and obvious to a user.
 */
@RunWith(AndroidJUnit4::class)
class IslandRendererInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    private val registry = IslandRendererRegistry(defaultIslandRenderers())

    @Before
    fun initGraph() {
        // DemoEvents builds its samples through the real factories, which need the object graph.
        AppGraph.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun theExpandedMediaCardShowsTrackAndArtist() {
        compose.setContent {
            IslandPreview(
                event = DemoEvents.music(),
                settings = IslandSettings.Default,
                registry = registry,
                expanded = true,
            )
        }

        compose.onNodeWithText("Midnight City Lights").assertExists()
        compose.onNodeWithText("Neon Harbour").assertExists()
    }

    @Test
    fun theCollapsedMediaPillShowsTheTrack() {
        compose.setContent {
            IslandPreview(
                event = DemoEvents.music(),
                settings = IslandSettings.Default,
                registry = registry,
            )
        }

        compose.onNodeWithText("Midnight City Lights").assertExists()
    }

    @Test
    fun theNotificationPillShowsTheSender() {
        compose.setContent {
            IslandPreview(
                event = DemoEvents.message(),
                settings = IslandSettings.Default,
                registry = registry,
            )
        }

        compose.onNodeWithText("Ava Chen").assertExists()
    }

    @Test
    fun theTimerPillShowsTheLabel() {
        compose.setContent {
            IslandPreview(
                event = DemoEvents.timer(),
                settings = IslandSettings.Default,
                registry = registry,
            )
        }

        compose.onNodeWithText("Pasta").assertExists()
    }

    @Test
    fun aGroupedNotificationShowsHowManyAreInside() {
        compose.setContent {
            IslandPreview(
                event = DemoEvents.groupedMessages(),
                settings = IslandSettings.Default,
                registry = registry,
            )
        }

        compose.onNodeWithText("Design review").assertExists()
        // Three messages in the thread: the pill carries the count chip. onAllNodesWithText rather
        // than onNodeWithText because a bare "3" could legitimately appear more than once.
        assertTrue(
            "the grouped pill did not show its message count",
            compose.onAllNodesWithText("3").fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
