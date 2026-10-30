package com.dynamicisland.engine.service

import com.dynamicisland.core.geometry.InteractionMode
import com.dynamicisland.engine.core.bus.IslandUserCommand
import com.dynamicisland.engine.core.model.IslandState

/** The gesture has already resolved to "open controls"; do not reinterpret it as a tap. */
internal object PanelExpansionPolicy {
    fun commandFor(state: IslandState, secondary: Boolean, mode: InteractionMode): IslandUserCommand = when {
        // Idle inbox and alert handling are shared by both interaction modes.
        state is IslandState.Closed -> IslandUserCommand.OpenInbox
        state is IslandState.AlertPeek -> IslandUserCommand.TapPrimary
        mode == InteractionMode.APPLE_PARITY ->
            if (secondary) IslandUserCommand.LongPressSecondary else IslandUserCommand.LongPressPrimary
        else -> if (secondary) IslandUserCommand.TapSecondary else IslandUserCommand.TapPrimary
    }
}
