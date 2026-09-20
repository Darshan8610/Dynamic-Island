package dev.island.core.platform

import android.os.SystemClock
import dev.island.domain.engine.IslandClock

/** Production clock: wall time for timestamps, monotonic uptime for every duration. */
object SystemIslandClock : IslandClock {
    override fun wallClockMs(): Long = System.currentTimeMillis()
    override fun elapsedMs(): Long = SystemClock.elapsedRealtime()
}

/** Fixed clock for deterministic tests. */
class TestIslandClock(var wallMs: Long = 0L, var elapsedMs: Long = 0L) : IslandClock {
    override fun wallClockMs(): Long = wallMs
    override fun elapsedMs(): Long = elapsedMs

    fun advance(ms: Long) {
        wallMs += ms
        elapsedMs += ms
    }
}
