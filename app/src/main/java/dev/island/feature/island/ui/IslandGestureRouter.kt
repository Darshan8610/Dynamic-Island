package dev.island.feature.island.ui

import dev.island.core.logging.IslandLogger
import dev.island.domain.engine.IslandEngine
import dev.island.domain.model.DoubleTapAction
import dev.island.domain.model.HorizontalSwipeAction
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.LongPressAction
import dev.island.domain.model.TapAction
import dev.island.domain.model.VerticalSwipeAction

/**
 * Maps raw gestures onto engine commands according to the user's behaviour settings.
 *
 * This is the seam that keeps Composables free of business logic: [IslandOverlayRoot] only reports
 * that a finger did something, and this class decides what that means today (single tap expands,
 * long press pins, swipe up dismisses… all configurable). It is plain Kotlin, so the mapping is
 * unit-testable without a device, and swapping a gesture's meaning never touches rendering code.
 *
 * Every handler is idempotent and cheap: the engine owns all state, so a repeated callback (a
 * double tap also delivering two single taps, for instance) can never corrupt anything — the state
 * machine ignores transitions that are not valid from the current phase.
 */
class IslandGestureRouter(
    private val engine: IslandEngine,
    private val settingsProvider: () -> IslandSettings,
    private val onOpenIslandSettings: () -> Unit,
    private val onOpenSourceApp: () -> Unit,
    private val onHaptic: () -> Unit,
    private val logger: IslandLogger,
) {

    private val expanded: Boolean get() = engine.uiState.value.expanded

    /** Builds the callbacks for the current settings snapshot. Called from composition. */
    fun callbacks(): IslandGestureCallbacks {
        val behavior = settingsProvider().behavior
        return IslandGestureCallbacks(
            enabled = true,
            onTap = {
                onHaptic()
                when (behavior.tapAction) {
                    TapAction.EXPAND -> if (expanded) engine.collapse() else engine.expand()
                    TapAction.EXPAND_OR_OPEN_SOURCE ->
                        if (expanded) onOpenSourceApp() else engine.expand()

                    TapAction.OPEN_SOURCE_APP -> onOpenSourceApp()
                }
            },
            onDoubleTap = {
                onHaptic()
                when (behavior.doubleTapAction) {
                    DoubleTapAction.TOGGLE_EXPAND -> engine.toggle()
                    DoubleTapAction.DISMISS -> engine.dismissFocused()
                    DoubleTapAction.CYCLE_EVENT -> engine.cycleEvent(1)
                }
            },
            onLongPress = {
                onHaptic()
                when (behavior.longPressAction) {
                    LongPressAction.OPEN_ISLAND_SETTINGS -> onOpenIslandSettings()
                    LongPressAction.OPEN_SOURCE_APP -> onOpenSourceApp()
                    LongPressAction.DISMISS_EVENT -> engine.dismissFocused()
                    LongPressAction.PIN_EVENT -> engine.pinFocused()
                }
            },
            onSwipe = { direction ->
                when (direction) {
                    SwipeDirection.LEFT -> horizontal(behavior.horizontalSwipeAction, delta = 1)
                    SwipeDirection.RIGHT -> horizontal(behavior.horizontalSwipeAction, delta = -1)
                    SwipeDirection.UP -> vertical(behavior.swipeUpAction)
                    SwipeDirection.DOWN -> vertical(behavior.swipeDownAction)
                }
            },
            onDragStart = { engine.beginInteraction() },
            onDrag = { _, _ -> },
            onDragEnd = { engine.endInteraction() },
        )
    }

    private fun horizontal(action: HorizontalSwipeAction, delta: Int) {
        onHaptic()
        when (action) {
            HorizontalSwipeAction.CYCLE_EVENT -> engine.cycleEvent(delta)
            HorizontalSwipeAction.DISMISS_EVENT -> engine.dismissFocused()
            HorizontalSwipeAction.NONE -> logger.d(TAG, "horizontal swipe ignored (disabled)")
        }
    }

    private fun vertical(action: VerticalSwipeAction) {
        onHaptic()
        when (action) {
            // "Expand" on a swipe down means toggle: down expands, and down while expanded collapses.
            VerticalSwipeAction.EXPAND -> engine.toggle()
            VerticalSwipeAction.DISMISS_EVENT -> engine.dismissFocused()
            VerticalSwipeAction.NONE -> logger.d(TAG, "vertical swipe ignored (disabled)")
        }
    }

    companion object {
        private const val TAG = "GestureRouter"
    }
}
