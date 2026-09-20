package dev.island.domain.model

/**
 * Countdown timer state. All timings are derived from
 * [android.os.SystemClock.elapsedRealtime] so a wall-clock change can never corrupt them.
 */
data class TimerInfo(
    val timerId: String,
    val label: String? = null,
    val totalMs: Long,
    val remainingMs: Long,
    val running: Boolean = false,
    val finished: Boolean = false,
    /** Monotonic timestamp of the last [remainingMs] sample; 0 when paused or finished. */
    val sampledAtElapsedMs: Long = 0L,
    val index: Int = 0,
    val activeTimerCount: Int = 1,
) {
    fun remainingAt(elapsedNowMs: Long): Long {
        if (!running || finished) return remainingMs.coerceAtLeast(0L)
        val elapsed = (elapsedNowMs - sampledAtElapsedMs).coerceAtLeast(0L)
        return (remainingMs - elapsed).coerceAtLeast(0L)
    }

    /** 1f at start → 0f at completion, for the island progress ring. */
    fun remainingFractionAt(elapsedNowMs: Long): Float {
        if (totalMs <= 0L) return 0f
        return (remainingAt(elapsedNowMs).toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    }
}

/** Stopwatch with lap support. Monotonic, so system clock changes cannot affect it. */
data class StopwatchInfo(
    val id: String = "stopwatch",
    val elapsedMs: Long = 0L,
    val running: Boolean = false,
    val sampledAtElapsedMs: Long = 0L,
    val laps: List<Long> = emptyList(),
) {
    fun elapsedAt(elapsedNowMs: Long): Long {
        if (!running) return elapsedMs
        return elapsedMs + (elapsedNowMs - sampledAtElapsedMs).coerceAtLeast(0L)
    }

    /** Lap splits (delta from previous lap), newest last. */
    fun splits(): List<Long> {
        if (laps.isEmpty()) return emptyList()
        return laps.mapIndexed { index, total ->
            if (index == 0) total else total - laps[index - 1]
        }
    }
}

/** Preset durations offered by the timer screen. */
enum class TimerPreset(val minutes: Int) {
    FIVE(5),
    TEN(10),
    TWENTY_FIVE(25),
    THIRTY(30),
    FORTY_FIVE(45),
    SIXTY(60),
    ;

    val durationMs: Long get() = minutes * 60_000L
}
