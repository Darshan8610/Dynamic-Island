package com.dynamicisland.engine

import com.dynamicisland.core.geometry.InteractionMode
import com.dynamicisland.engine.arbiter.IslandPriorityArbiter
import com.dynamicisland.engine.core.bus.*
import com.dynamicisland.engine.core.model.*
import com.dynamicisland.engine.service.PanelExpansionPolicy
import com.dynamicisland.engine.settings.BehaviorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RepairRegressionTest {
    private fun timer(key: String = "timer", actions: List<IslandAction> = emptyList()) =
        SystemIngestionEvent.NotificationEvent.Posted(
            key, "clock.app", 1, "alarm", true, "Timer", "00:30", null,
            null, null, false, null, false, actions = actions,
            contentIntent = null, statusBarNotification = null, shouldAlert = false
        )

    @Test fun `resolved open controls expands primary in both interaction modes`() = runTest(UnconfinedTestDispatcher()) {
        for (mode in InteractionMode.values()) {
            val bus = IslandEventBus()
            val arbiter = IslandPriorityArbiter(bus, backgroundScope, behavior = MutableStateFlow(BehaviorOptions(interactionMode = mode)))
            bus.emitEvent(timer()); runCurrent()
            bus.emitCommand(PanelExpansionPolicy.commandFor(arbiter.islandState.value, false, mode)); runCurrent()
            assertEquals(mode.name, "timer", (arbiter.islandState.value as IslandState.Expanded).activity.id)
        }
    }

    @Test fun `resolved open controls expands secondary in Apple parity mode`() = runTest(UnconfinedTestDispatcher()) {
        val bus = IslandEventBus()
        val mode = InteractionMode.APPLE_PARITY
        val arbiter = IslandPriorityArbiter(bus, backgroundScope, behavior = MutableStateFlow(BehaviorOptions(interactionMode = mode)))
        bus.emitEvent(timer())
        bus.emitEvent(SystemIngestionEvent.Media.PlaybackChanged("player.app", true, 3, 0, 1000, 1f, 0))
        runCurrent()
        val secondary = (arbiter.islandState.value as IslandState.Minimal).secondary.id
        bus.emitCommand(PanelExpansionPolicy.commandFor(arbiter.islandState.value, true, mode)); runCurrent()
        assertEquals(secondary, (arbiter.islandState.value as IslandState.Expanded).activity.id)
    }

    @Test fun `open controls from idle reaches retained inbox in Apple parity mode`() = runTest(UnconfinedTestDispatcher()) {
        val bus = IslandEventBus()
        val mode = InteractionMode.APPLE_PARITY
        val arbiter = IslandPriorityArbiter(bus, backgroundScope, behavior = MutableStateFlow(BehaviorOptions(interactionMode = mode)))
        bus.emitEvent(timer().copy(key = "message", category = null, isOngoing = false))
        bus.emitCommand(IslandUserCommand.DismissAlertPeek); runCurrent()
        assertEquals(IslandState.Closed, arbiter.islandState.value)
        bus.emitCommand(PanelExpansionPolicy.commandFor(arbiter.islandState.value, false, mode)); runCurrent()
        assertTrue(arbiter.islandState.value is IslandState.NotificationInbox)
    }

    @Test fun `queued activity actions are blocked after lock and resume after unlock`() = runTest(UnconfinedTestDispatcher()) {
        val bus = IslandEventBus()
        IslandPriorityArbiter(bus, backgroundScope)
        var calls = 0
        bus.emitEvent(timer(actions = listOf(IslandAction("action", "Action", actionCallback = { calls++ }))))
        runCurrent()
        bus.emitCommand(IslandUserCommand.ExecuteAction("timer", "action")); runCurrent()
        assertEquals(1, calls)
        bus.emitEvent(SystemIngestionEvent.SystemState.KeyguardLocked(true)); runCurrent()
        bus.emitCommand(IslandUserCommand.ExecuteAction("timer", "action")); runCurrent()
        assertEquals(1, calls)
        bus.emitEvent(SystemIngestionEvent.SystemState.KeyguardLocked(false)); runCurrent()
        bus.emitCommand(IslandUserCommand.ExecuteAction("timer", "action")); runCurrent()
        assertEquals(2, calls)
    }
}
