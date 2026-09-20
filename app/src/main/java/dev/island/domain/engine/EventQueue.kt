package dev.island.domain.engine

import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandPriority

/**
 * The ordered set of events the island knows about, plus which one currently owns the display.
 *
 * Invariants:
 * - events are unique by [IslandEvent.coalesceKey];
 * - [focusId] always points at an event in [events] (or is null when empty);
 * - [generation] increments exactly when focus changes, which is the renderer's key for
 *   "play the intro animation" — a refreshed event never re-animates.
 */
data class EventStack(
    val events: List<IslandEvent> = emptyList(),
    val focusId: String? = null,
    val generation: Int = 0,
) {
    val focused: IslandEvent? get() = events.firstOrNull { it.id == focusId }
    val hasEvents: Boolean get() = events.isNotEmpty()
    val hasPersistentEvents: Boolean get() = events.any { it.persistent }
    val hasTransientEvents: Boolean get() = events.any { !it.persistent }
    val focusedIsPersistent: Boolean get() = focused?.persistent == true

    /** Focus first, then the rest in display order — used for swipe browsing and the queue dots. */
    val browsable: List<IslandEvent>
        get() {
            val f = focused ?: return events
            return listOf(f) + events.filterNot { it.id == f.id }
        }

    companion object {
        val Empty = EventStack()
    }
}

sealed interface QueueCommand {
    data class Upsert(val event: IslandEvent) : QueueCommand
    data class Remove(val eventId: String) : QueueCommand
    data object Clear : QueueCommand
    data class Focus(val eventId: String) : QueueCommand

    /** +1 next event, -1 previous event (horizontal swipe). */
    data class Cycle(val delta: Int) : QueueCommand
}

/** Outcome of a queue mutation; the engine turns this into state-machine triggers. */
data class QueueResult(
    val stack: EventStack,
    val focusChanged: Boolean,
    /** True when an event with the same coalesce key was refreshed rather than added. */
    val wasUpdate: Boolean,
    val removedIds: List<String> = emptyList(),
)

/**
 * The priority + pinning rules of the island, as a pure reducer.
 *
 * - Higher priority wins focus.
 * - A temporary event takes the display over a persistent one ("music + WhatsApp"), and focus
 *   returns to the persistent event as soon as the temporary one goes away — unless the
 *   persistent event is CRITICAL (an active call is never covered).
 * - Equal priority keeps the current focus (stability) or the oldest event after a removal.
 */
object EventQueue {

    /** Hard cap so a pathological producer can never grow the stack without bound. */
    const val MAX_EVENTS = 8

    fun reduce(stack: EventStack, command: QueueCommand): QueueResult = when (command) {
        is QueueCommand.Upsert -> upsert(stack, command.event)
        is QueueCommand.Remove -> remove(stack, command.eventId)
        QueueCommand.Clear -> QueueResult(EventStack.Empty, focusChanged = stack.focusId != null, wasUpdate = false, removedIds = stack.events.map { it.id })
        is QueueCommand.Focus -> focus(stack, command.eventId)
        is QueueCommand.Cycle -> cycle(stack, command.delta)
    }

    private fun upsert(stack: EventStack, event: IslandEvent): QueueResult {
        val existingIndex = stack.events.indexOfFirst { it.coalesceKey == event.coalesceKey }
        val existing = stack.events.getOrNull(existingIndex)

        // Keep identity + original arrival time so a refreshed event does not re-animate.
        val merged: IslandEvent = if (existing != null) {
            event.withMeta(
                event.meta.copy(
                    id = existing.id,
                    createdAt = existing.createdAt,
                ),
            )
        } else {
            event
        }

        val withoutDuplicate = if (existingIndex >= 0) stack.events.toMutableList().also { it[existingIndex] = merged }
        else stack.events + merged

        val capped = cap(withoutDuplicate, keepId = merged.id)
        val newFocus = selectFocus(stack, capped, incoming = merged, wasUpdate = existing != null)
        val focusChanged = newFocus != stack.focusId

        return QueueResult(
            stack = EventStack(
                events = capped,
                focusId = newFocus,
                generation = if (focusChanged) stack.generation + 1 else stack.generation,
            ),
            focusChanged = focusChanged,
            wasUpdate = existing != null,
        )
    }

    private fun remove(stack: EventStack, eventId: String): QueueResult {
        val remaining = stack.events.filterNot { it.id == eventId }
        if (remaining.size == stack.events.size) {
            return QueueResult(stack, focusChanged = false, wasUpdate = false)
        }
        val newFocus = if (stack.focusId == eventId) selectFocus(stack, remaining, incoming = null, wasUpdate = false) else stack.focusId
        val focusChanged = newFocus != stack.focusId
        return QueueResult(
            stack = EventStack(
                events = remaining,
                focusId = newFocus,
                generation = if (focusChanged) stack.generation + 1 else stack.generation,
            ),
            focusChanged = focusChanged,
            wasUpdate = false,
            removedIds = listOf(eventId),
        )
    }

    private fun focus(stack: EventStack, eventId: String): QueueResult {
        if (stack.events.none { it.id == eventId } || stack.focusId == eventId) {
            return QueueResult(stack, focusChanged = false, wasUpdate = false)
        }
        return QueueResult(
            stack = stack.copy(focusId = eventId, generation = stack.generation + 1),
            focusChanged = true,
            wasUpdate = false,
        )
    }

    private fun cycle(stack: EventStack, delta: Int): QueueResult {
        if (stack.events.size < 2 || delta == 0) return QueueResult(stack, focusChanged = false, wasUpdate = false)
        val ordered = displayOrder(stack.events)
        val currentIndex = ordered.indexOfFirst { it.id == stack.focusId }.let { if (it < 0) 0 else it }
        val nextIndex = ((currentIndex + delta) % ordered.size + ordered.size) % ordered.size
        val next = ordered[nextIndex]
        if (next.id == stack.focusId) return QueueResult(stack, focusChanged = false, wasUpdate = false)
        return QueueResult(
            stack = stack.copy(focusId = next.id, generation = stack.generation + 1),
            focusChanged = true,
            wasUpdate = false,
        )
    }

    /** Stable display order: priority first, then oldest first so focus does not jitter. */
    fun displayOrder(events: List<IslandEvent>): List<IslandEvent> =
        events.sortedWith(
            compareByDescending<IslandEvent> { it.priority.rank }
                .thenBy { it.createdAt },
        )

    private fun cap(events: List<IslandEvent>, keepId: String): List<IslandEvent> {
        if (events.size <= MAX_EVENTS) return events
        val ordered = displayOrder(events)
        val keep = ordered.take(MAX_EVENTS).toMutableList()
        if (keep.none { it.id == keepId }) {
            keep[keep.lastIndex] = events.first { it.id == keepId }
        }
        return displayOrder(keep)
    }

    private fun selectFocus(
        previous: EventStack,
        events: List<IslandEvent>,
        incoming: IslandEvent?,
        wasUpdate: Boolean,
    ): String? {
        if (events.isEmpty()) return null
        val current = events.firstOrNull { it.id == previous.focusId }

        if (current == null) {
            // Focus was removed: hand the island back to the best remaining event.
            return displayOrder(events).first().id
        }

        val new = incoming ?: return current.id
        if (wasUpdate && new.id == current.id) return current.id

        val criticalHolds = current.persistent && current.priority == IslandPriority.CRITICAL
        if (criticalHolds) return current.id

        return when {
            // A temporary event briefly takes over a persistent mini island.
            !new.persistent && current.persistent -> new.id
            new.priority.rank > current.priority.rank -> new.id
            else -> current.id
        }
    }
}
