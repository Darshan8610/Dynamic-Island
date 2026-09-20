package dev.island.domain.engine

import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandEventMeta

/**
 * Identity-preserving metadata replacement. Used by the privacy masker and the queue, so an
 * updated event never loses its place in the stack (and never re-runs its intro animation).
 */
fun IslandEvent.withMeta(meta: IslandEventMeta): IslandEvent = when (this) {
    is IslandEvent.Notification -> copy(meta = meta)
    is IslandEvent.Media -> copy(meta = meta)
    is IslandEvent.Timer -> copy(meta = meta)
    is IslandEvent.Stopwatch -> copy(meta = meta)
    is IslandEvent.Call -> copy(meta = meta)
    is IslandEvent.Charging -> copy(meta = meta)
    is IslandEvent.Battery -> copy(meta = meta)
    is IslandEvent.Bluetooth -> copy(meta = meta)
    is IslandEvent.Navigation -> copy(meta = meta)
    is IslandEvent.Download -> copy(meta = meta)
    is IslandEvent.Alarm -> copy(meta = meta)
    is IslandEvent.System -> copy(meta = meta)
    is IslandEvent.Custom -> copy(meta = meta)
}

/** True when this event keeps a compact mini island alive instead of disappearing. */
val IslandEvent.ownsMiniIsland: Boolean
    get() = persistent || expiresAt == null
