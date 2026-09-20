package dev.island.domain.engine

/**
 * Time abstraction so the engine is deterministic under test.
 * Production implementation: [dev.island.core.platform.SystemIslandClock].
 */
interface IslandClock {
    /** Wall clock, for absolute timestamps and deadlines. */
    fun wallClockMs(): Long

    /** Monotonic uptime, for durations that must survive wall-clock changes. */
    fun elapsedMs(): Long
}
