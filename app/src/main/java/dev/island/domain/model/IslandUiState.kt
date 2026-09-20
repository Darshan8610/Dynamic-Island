package dev.island.domain.model

/**
 * The island UI state machine. Transitions are produced by
 * [dev.island.domain.engine.IslandStateMachine] and consumed by the overlay renderer.
 */
enum class IslandPhase {
    /** Nothing to show; the overlay window is hidden (or not attached at all). */
    IDLE,

    /** Compact pill with a persistent event (music, timer, navigation, download…). */
    COLLAPSED,

    /** Animating from compact to full card. */
    EXPANDING,

    /** Full, event-specific card. */
    EXPANDED,

    /** The user is dragging/swiping; auto-collapse timers are suspended. */
    INTERACTING,

    /** Animating back to compact or idle. */
    COLLAPSING,

    /** Short-lived event fully shown; will auto-collapse after its own timeout. */
    TRANSIENT,

    /** A temporary event is pinned above a persistent one (notification over music). */
    PINNED,

    /** Island is switched off, or a required permission is missing. */
    DISABLED,
}

/** Why the island is not rendering. Every reason has an in-app explanation and a fix path. */
enum class DisabledReason {
    USER_DISABLED,
    OVERLAY_PERMISSION_MISSING,
    NOTIFICATION_ACCESS_MISSING,
    SCREEN_OFF,
    LANDSCAPE_HIDDEN,
    SERVICE_STOPPED,
}

/**
 * Single immutable snapshot the overlay renders from. Kept small and stable on purpose:
 * the overlay only recomposes when one of these fields actually changes.
 */
data class IslandUiState(
    val phase: IslandPhase = IslandPhase.DISABLED,
    val events: List<IslandEvent> = emptyList(),
    val focusedEventId: String? = null,
    /** Increments exactly when focus changes; the renderer keys its intro animation on this. */
    val focusGeneration: Int = 0,
    val expanded: Boolean = false,
    val userInteracting: Boolean = false,
    val enabled: Boolean = false,
    val disabledReason: DisabledReason? = DisabledReason.USER_DISABLED,
    val screenLocked: Boolean = false,
    val screenOff: Boolean = false,
    val doNotDisturb: Boolean = false,
    val landscape: Boolean = false,
    val lastEventAtMs: Long = 0L,
    val lastTransitionAtMs: Long = 0L,
) {
    val focusedEvent: IslandEvent?
        get() = events.firstOrNull { it.id == focusedEventId } ?: events.firstOrNull()

    /** Events other than the focused one, for the queue indicator and swipe browsing. */
    val otherEvents: List<IslandEvent>
        get() = events.filterNot { it.id == focusedEvent?.id }

    val hasEvents: Boolean get() = events.isNotEmpty()

    /**
     * Alias used by the UI and service layers: everything currently on the stack, oldest first.
     * Same list as [events], named for the way it is read at the call site.
     */
    val activeEvents: List<IslandEvent> get() = events

    val isRendering: Boolean
        get() = enabled && disabledReason == null && phase != IslandPhase.IDLE && hasEvents

    val isPersistentOnly: Boolean
        get() = hasEvents && events.all { it.persistent }

    companion object {
        val Disabled = IslandUiState()
    }
}
