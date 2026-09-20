package dev.island.domain.engine

import dev.island.domain.model.DisabledReason
import dev.island.domain.model.IslandPhase
import dev.island.domain.model.IslandPriority

/** Everything that can move the island from one phase to another. */
sealed interface IslandTrigger {

    data class EventArrived(
        val priority: IslandPriority,
        val persistent: Boolean,
        /** True when an already visible event was merely refreshed (progress tick, metadata change). */
        val isUpdate: Boolean = false,
        /** True when the focused event changed, i.e. the island must play an intro animation. */
        val focusChanged: Boolean = true,
    ) : IslandTrigger

    /** The expanded intro window elapsed (persistent events compact into the mini island). */
    data object IntroElapsed : IslandTrigger

    /** The transient display window elapsed. */
    data object AutoCollapseElapsed : IslandTrigger

    data object ExpandRequested : IslandTrigger
    data object CollapseRequested : IslandTrigger
    data object InteractionStarted : IslandTrigger
    data object InteractionEnded : IslandTrigger

    /** Emitted by the renderer when an expand/collapse animation finished. */
    data object AnimationCompleted : IslandTrigger

    data object UserPinned : IslandTrigger
    data object UserUnpinned : IslandTrigger

    data class EventRemoved(val remainingEvents: Int) : IslandTrigger
    data object QueueCleared : IslandTrigger

    data class Disabled(val reason: DisabledReason) : IslandTrigger
    data object Enabled : IslandTrigger
    data object ScreenTurnedOff : IslandTrigger
    data object ScreenTurnedOn : IslandTrigger
}

/** Minimal facts the transition table needs; keeps [IslandStateMachine] a pure function. */
data class TransitionContext(
    val hasEvents: Boolean = false,
    val hasPersistentEvents: Boolean = false,
    val hasTransientEvents: Boolean = false,
    val expandedByUser: Boolean = false,
    val pinnedByUser: Boolean = false,
)

/**
 * The island state machine.
 *
 * Transient event:
 * ```
 * IDLE → EventArrived → TRANSIENT → AutoCollapseElapsed → COLLAPSING → AnimationCompleted → IDLE
 * ```
 *
 * Persistent event (music, timer, navigation, download):
 * ```
 * IDLE → EventArrived → EXPANDING → AnimationCompleted → EXPANDED → IntroElapsed
 *      → COLLAPSING → AnimationCompleted → COLLAPSED (persistent mini island)
 * ```
 *
 * Temporary event over a persistent one:
 * ```
 * COLLAPSED → EventArrived(transient) → PINNED → AutoCollapseElapsed → COLLAPSING → COLLAPSED
 * ```
 *
 * The function is total: every phase/trigger pair produces a defined phase, which is what makes
 * the acceptance scenarios in IslandStateMachineTest enumerable.
 */
object IslandStateMachine {

    /** Phases in which the island is drawn at full card size. */
    val expandedPhases: Set<IslandPhase> = setOf(
        IslandPhase.TRANSIENT,
        IslandPhase.EXPANDING,
        IslandPhase.EXPANDED,
        IslandPhase.INTERACTING,
        IslandPhase.PINNED,
    )

    fun isExpandedPhase(phase: IslandPhase): Boolean = phase in expandedPhases

    fun next(current: IslandPhase, trigger: IslandTrigger, context: TransitionContext): IslandPhase {
        // Hard overrides, evaluated before anything else.
        if (trigger is IslandTrigger.Disabled) return IslandPhase.DISABLED
        if (trigger == IslandTrigger.ScreenTurnedOff) return IslandPhase.IDLE
        if (current == IslandPhase.DISABLED) {
            // Only an explicit enable (or the screen coming back on) leaves DISABLED.
            return when (trigger) {
                IslandTrigger.Enabled, IslandTrigger.ScreenTurnedOn -> settle(context)
                else -> IslandPhase.DISABLED
            }
        }

        return when (trigger) {
            is IslandTrigger.Disabled -> IslandPhase.DISABLED
            IslandTrigger.ScreenTurnedOff -> IslandPhase.IDLE

            IslandTrigger.Enabled, IslandTrigger.ScreenTurnedOn -> settle(context)

            is IslandTrigger.EventArrived -> onEventArrived(current, trigger, context)

            IslandTrigger.IntroElapsed -> when (current) {
                IslandPhase.EXPANDED, IslandPhase.PINNED, IslandPhase.TRANSIENT, IslandPhase.EXPANDING ->
                    IslandPhase.COLLAPSING

                else -> current
            }

            IslandTrigger.AutoCollapseElapsed -> when (current) {
                // Auto-collapse is suspended while the user is touching the island.
                IslandPhase.INTERACTING, IslandPhase.IDLE, IslandPhase.COLLAPSING, IslandPhase.COLLAPSED -> current
                else -> IslandPhase.COLLAPSING
            }

            IslandTrigger.ExpandRequested -> when {
                !context.hasEvents -> current
                current == IslandPhase.EXPANDED || current == IslandPhase.EXPANDING -> current
                else -> IslandPhase.EXPANDING
            }

            IslandTrigger.CollapseRequested -> when (current) {
                IslandPhase.IDLE, IslandPhase.COLLAPSED, IslandPhase.COLLAPSING -> current
                else -> IslandPhase.COLLAPSING
            }

            IslandTrigger.InteractionStarted -> when (current) {
                IslandPhase.IDLE, IslandPhase.COLLAPSING, IslandPhase.EXPANDING -> current
                else -> IslandPhase.INTERACTING
            }

            IslandTrigger.InteractionEnded -> when (current) {
                IslandPhase.INTERACTING -> if (context.expandedByUser) IslandPhase.EXPANDED else IslandPhase.COLLAPSED
                else -> current
            }

            IslandTrigger.AnimationCompleted -> when (current) {
                IslandPhase.EXPANDING -> IslandPhase.EXPANDED
                IslandPhase.COLLAPSING -> settleAfterCollapse(context)
                else -> current
            }

            IslandTrigger.UserPinned -> if (context.hasEvents) IslandPhase.PINNED else current
            IslandTrigger.UserUnpinned -> if (current == IslandPhase.PINNED) settle(context) else current

            is IslandTrigger.EventRemoved -> when {
                trigger.remainingEvents <= 0 -> if (current == IslandPhase.IDLE) current else IslandPhase.COLLAPSING
                // The pinned transient event went away: hand the island back to the persistent one.
                current == IslandPhase.PINNED && !context.hasTransientEvents -> IslandPhase.COLLAPSING
                current == IslandPhase.TRANSIENT && !context.hasTransientEvents -> IslandPhase.COLLAPSING
                else -> current
            }

            IslandTrigger.QueueCleared ->
                if (current == IslandPhase.IDLE) current else IslandPhase.COLLAPSING
        }
    }

    private fun onEventArrived(
        current: IslandPhase,
        trigger: IslandTrigger.EventArrived,
        context: TransitionContext,
    ): IslandPhase {
        if (!trigger.focusChanged) return current
        return when (current) {
            IslandPhase.IDLE -> if (trigger.persistent) IslandPhase.EXPANDING else IslandPhase.TRANSIENT

            // Compact mini island + temporary event => pin it on top, then hand focus back.
            IslandPhase.COLLAPSED -> when {
                !trigger.persistent -> IslandPhase.PINNED
                !trigger.isUpdate -> IslandPhase.EXPANDING
                else -> IslandPhase.COLLAPSED
            }

            IslandPhase.COLLAPSING -> if (trigger.persistent) IslandPhase.EXPANDING else IslandPhase.TRANSIENT

            // Already showing something at full size: crossfade content, do not re-run the intro.
            IslandPhase.TRANSIENT, IslandPhase.PINNED, IslandPhase.EXPANDED, IslandPhase.EXPANDING -> current

            IslandPhase.INTERACTING -> current

            // A disabled island never animates in; next() short-circuits before reaching here.
            IslandPhase.DISABLED -> IslandPhase.DISABLED
        }
    }

    private fun settleAfterCollapse(context: TransitionContext): IslandPhase = when {
        !context.hasEvents -> IslandPhase.IDLE
        context.hasTransientEvents -> IslandPhase.TRANSIENT
        context.hasPersistentEvents -> IslandPhase.COLLAPSED
        else -> IslandPhase.IDLE
    }

    private fun settle(context: TransitionContext): IslandPhase = when {
        !context.hasEvents -> IslandPhase.IDLE
        context.expandedByUser -> IslandPhase.EXPANDED
        context.hasTransientEvents -> IslandPhase.TRANSIENT
        context.hasPersistentEvents -> IslandPhase.COLLAPSED
        else -> IslandPhase.IDLE
    }
}
