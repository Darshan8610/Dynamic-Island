package dev.island.domain

import dev.island.FakeDeviceStateRepository
import dev.island.FakeSettingsRepository
import dev.island.TestEvents
import dev.island.core.logging.SilentIslandLogger
import dev.island.core.platform.TestIslandClock
import dev.island.domain.engine.IslandEngine
import dev.island.domain.engine.IslandStateMachine
import dev.island.domain.model.DeviceContext
import dev.island.domain.model.DisabledReason
import dev.island.domain.model.IslandPriority
import dev.island.domain.model.IslandSettings
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end tests of the engine's actor loop, on a virtual clock.
 *
 * These cover the contract the rest of the app depends on: submit → the island renders; dismiss →
 * it stops; stack → the right event is focused; disable → nothing renders. Time is advanced
 * explicitly (never `advanceUntilIdle`) so a test observes the island *while* an event is alive
 * instead of fast-forwarding past its expiration.
 */
class IslandEngineTest {

    private class Harness(
        settings: IslandSettings = IslandSettings(islandEnabled = true),
    ) {
        val clock = TestIslandClock(wallMs = 1_700_000_000_000L, elapsedMs = 5_000L)
        val settingsRepository = FakeSettingsRepository(settings)
        val deviceStateRepository = FakeDeviceStateRepository()
        lateinit var engine: IslandEngine

        fun create(scope: kotlinx.coroutines.CoroutineScope): IslandEngine {
            engine = IslandEngine(
                scope = scope,
                settingsRepository = settingsRepository,
                deviceStateRepository = deviceStateRepository,
                clock = clock,
                logger = SilentIslandLogger,
            )
            return engine
        }
    }

    @Test
    fun `a submitted event is focused and rendered`() = runTest {
        val harness = Harness()
        val engine = harness.create(backgroundScope)

        engine.start()
        runCurrent()
        engine.setEnabled(true)
        engine.submit(TestEvents.notification(id = "n1"))
        advanceTimeBy(SETTLE_MS)
        runCurrent()

        val state = engine.uiState.value
        assertTrue("engine must be enabled", state.enabled)
        assertTrue("the event must be on the stack", state.hasEvents)
        assertEquals("n1", state.focusedEventId)
        assertTrue("the island must be rendering", state.isRendering)
        assertNotNull(state.focusedEvent)

        engine.stop()
    }

    @Test
    fun `a second event stacks and the newest one takes the display`() = runTest {
        val harness = Harness()
        val engine = harness.create(backgroundScope)

        engine.start()
        runCurrent()
        engine.setEnabled(true)
        engine.submit(TestEvents.media(id = "music"))
        advanceTimeBy(SETTLE_MS)
        runCurrent()
        engine.submit(TestEvents.notification(id = "message"))
        advanceTimeBy(SETTLE_MS)
        runCurrent()

        val state = engine.uiState.value
        assertEquals(2, state.activeEvents.size)
        assertEquals("message", state.focusedEventId)
        assertTrue(state.isPersistentOnly.not())

        engine.stop()
    }

    @Test
    fun `dismissing the focused event empties the island`() = runTest {
        val harness = Harness()
        val engine = harness.create(backgroundScope)

        engine.start()
        runCurrent()
        engine.setEnabled(true)
        engine.submit(TestEvents.notification(id = "n1"))
        advanceTimeBy(SETTLE_MS)
        runCurrent()
        assertTrue(engine.uiState.value.hasEvents)

        engine.dismissFocused()
        advanceTimeBy(SETTLE_MS)
        runCurrent()

        assertFalse(engine.uiState.value.hasEvents)
        assertFalse(engine.uiState.value.isRendering)

        engine.stop()
    }

    @Test
    fun `expand and collapse are reflected in the published state`() = runTest {
        val harness = Harness()
        val engine = harness.create(backgroundScope)

        engine.start()
        runCurrent()
        engine.setEnabled(true)
        engine.submit(TestEvents.media(id = "music"))
        advanceTimeBy(SETTLE_MS)
        runCurrent()

        engine.expand()
        engine.onAnimationCompleted()
        advanceTimeBy(SETTLE_MS)
        runCurrent()
        val expanded = engine.uiState.value
        assertTrue(
            "phase was ${expanded.phase}",
            expanded.expanded || IslandStateMachine.isExpandedPhase(expanded.phase),
        )

        engine.collapse()
        engine.onAnimationCompleted()
        advanceTimeBy(SETTLE_MS)
        runCurrent()
        assertFalse(engine.uiState.value.expanded)

        engine.stop()
    }

    @Test
    fun `disabling stops rendering even with events on the stack`() = runTest {
        val harness = Harness()
        val engine = harness.create(backgroundScope)

        engine.start()
        runCurrent()
        engine.setEnabled(true)
        engine.submit(TestEvents.notification(id = "n1"))
        advanceTimeBy(SETTLE_MS)
        runCurrent()
        assertTrue(engine.uiState.value.isRendering)

        engine.setEnabled(false, DisabledReason.OVERLAY_PERMISSION_MISSING)
        advanceTimeBy(SETTLE_MS)
        runCurrent()

        val state = engine.uiState.value
        assertFalse(state.isRendering)
        assertEquals(DisabledReason.OVERLAY_PERMISSION_MISSING, state.disabledReason)

        engine.stop()
    }

    @Test
    fun `screen off hides the island and screen on brings it back`() = runTest {
        val harness = Harness()
        val engine = harness.create(backgroundScope)

        engine.start()
        runCurrent()
        engine.setEnabled(true)
        engine.submit(TestEvents.media(id = "music"))
        advanceTimeBy(SETTLE_MS)
        runCurrent()
        assertTrue(engine.uiState.value.isRendering)

        harness.deviceStateRepository.set(DeviceContext(screenOn = false))
        advanceTimeBy(SETTLE_MS)
        runCurrent()
        assertFalse("a screen-off device must not render the island", engine.uiState.value.isRendering)

        harness.deviceStateRepository.set(DeviceContext(screenOn = true))
        advanceTimeBy(SETTLE_MS)
        runCurrent()
        assertTrue(engine.uiState.value.hasEvents)

        engine.stop()
    }

    @Test
    fun `cycling moves focus between stacked events`() = runTest {
        val harness = Harness()
        val engine = harness.create(backgroundScope)

        engine.start()
        runCurrent()
        engine.setEnabled(true)
        engine.submit(TestEvents.media(id = "music"))
        advanceTimeBy(SETTLE_MS)
        runCurrent()
        engine.submit(TestEvents.notification(id = "message", priority = IslandPriority.MEDIUM))
        advanceTimeBy(SETTLE_MS)
        runCurrent()

        val before = engine.uiState.value.focusedEventId
        engine.cycleEvent(1)
        advanceTimeBy(SETTLE_MS)
        runCurrent()
        val after = engine.uiState.value.focusedEventId

        assertEquals(2, engine.uiState.value.activeEvents.size)
        assertTrue("focus did not move ($before -> $after)", before != after)

        engine.stop()
    }

    @Test
    fun `a coalesced refresh does not create a second event`() = runTest {
        val harness = Harness()
        val engine = harness.create(backgroundScope)

        engine.start()
        runCurrent()
        engine.setEnabled(true)
        engine.submit(TestEvents.notification(id = "n1", title = "First"))
        advanceTimeBy(SETTLE_MS)
        runCurrent()
        engine.submit(TestEvents.notification(id = "n1", title = "Second"))
        advanceTimeBy(SETTLE_MS)
        runCurrent()

        val state = engine.uiState.value
        assertEquals(1, state.activeEvents.size)
        assertEquals("Second", state.focusedEvent?.title)

        engine.stop()
    }

    private companion object {
        /** Long enough for the coalesce window, short enough that no event expires. */
        const val SETTLE_MS = 600L
    }
}
