package dev.island.domain.engine

import dev.island.domain.model.CallState
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandSettings

/**
 * Per-event timeouts. A single global timeout would either dismiss music immediately or keep
 * every notification on screen far too long, so each family gets its own policy.
 *
 * [collapseAfterMs] — how long the event stays fully expanded before compacting.
 * [removeAfterMs]   — how long the event stays in the stack at all (`null` = until the source clears it).
 */
data class TimeoutPolicy(
    val collapseAfterMs: Long?,
    val removeAfterMs: Long?,
) {
    val isPersistent: Boolean get() = removeAfterMs == null
}

object ExpirationPolicy {

    /** Default window a transient notification owns the island for. */
    const val NOTIFICATION_DISPLAY_MS = 6_000L
    const val NOTIFICATION_LINGER_MS = 2_500L
    const val CHARGING_DISPLAY_MS = 5_000L
    const val CHARGING_LINGER_MS = 4_000L
    const val BLUETOOTH_DISPLAY_MS = 4_000L
    const val BLUETOOTH_LINGER_MS = 2_500L
    const val BATTERY_DISPLAY_MS = 6_000L
    const val BATTERY_LINGER_MS = 6_000L
    const val SYSTEM_DISPLAY_MS = 4_000L
    const val SYSTEM_LINGER_MS = 2_500L
    const val NAVIGATION_DISPLAY_MS = 3_000L
    const val DOWNLOAD_DISPLAY_MS = 3_000L
    const val DOWNLOAD_COMPLETE_DISPLAY_MS = 4_000L
    const val DOWNLOAD_COMPLETE_LINGER_MS = 4_000L
    const val TIMER_DISPLAY_MS = 2_200L
    const val TIMER_COMPLETE_DISPLAY_MS = 8_000L
    const val TIMER_COMPLETE_LINGER_MS = 60_000L
    const val STOPWATCH_DISPLAY_MS = 2_000L
    const val CALL_ACTIVE_DISPLAY_MS = 3_500L
    const val CALL_ENDED_DISPLAY_MS = 3_000L
    const val CALL_ENDED_LINGER_MS = 3_500L
    const val ALARM_UPCOMING_DISPLAY_MS = 5_000L
    const val ALARM_UPCOMING_LINGER_MS = 4_000L
    const val CUSTOM_DISPLAY_MS = 5_000L
    const val CUSTOM_LINGER_MS = 3_000L
    const val MEDIA_INTRO_FALLBACK_MS = 2_600L

    fun policyFor(event: IslandEvent, settings: IslandSettings): TimeoutPolicy {
        // An explicit expiry set by the producer always wins.
        val explicit = event.meta.expiresAt
        if (explicit != null && !event.persistent) {
            val total = (explicit - event.meta.createdAt).coerceAtLeast(1_000L)
            return TimeoutPolicy(collapseAfterMs = (total * 0.6f).toLong(), removeAfterMs = total)
        }

        return when (event) {
            is IslandEvent.Notification -> {
                val display = settings.behavior.autoCollapseSeconds.coerceIn(2, 30) * 1_000L
                if (event.notification.isOngoing || event.notification.isForegroundService) {
                    TimeoutPolicy(collapseAfterMs = display, removeAfterMs = null)
                } else {
                    TimeoutPolicy(collapseAfterMs = display, removeAfterMs = display + NOTIFICATION_LINGER_MS)
                }
            }

            is IslandEvent.Media -> TimeoutPolicy(
                collapseAfterMs = settings.media.collapseAfterIntroMs.takeIf { it > 0 } ?: MEDIA_INTRO_FALLBACK_MS,
                removeAfterMs = null,
            )

            is IslandEvent.Timer ->
                if (event.timer.finished) {
                    TimeoutPolicy(TIMER_COMPLETE_DISPLAY_MS, TIMER_COMPLETE_DISPLAY_MS + TIMER_COMPLETE_LINGER_MS)
                } else {
                    TimeoutPolicy(TIMER_DISPLAY_MS, null)
                }

            is IslandEvent.Stopwatch ->
                if (event.stopwatch.running) {
                    TimeoutPolicy(STOPWATCH_DISPLAY_MS, null)
                } else {
                    TimeoutPolicy(STOPWATCH_DISPLAY_MS, STOPWATCH_DISPLAY_MS + CHARGING_LINGER_MS)
                }

            is IslandEvent.Call -> when (event.call.state) {
                // A ringing call keeps the island expanded until the user or the caller acts.
                CallState.INCOMING -> TimeoutPolicy(collapseAfterMs = null, removeAfterMs = null)
                CallState.ACTIVE, CallState.HOLDING -> TimeoutPolicy(CALL_ACTIVE_DISPLAY_MS, null)
                CallState.ENDED -> TimeoutPolicy(CALL_ENDED_DISPLAY_MS, CALL_ENDED_DISPLAY_MS + CALL_ENDED_LINGER_MS)
                CallState.UNKNOWN -> TimeoutPolicy(CALL_ACTIVE_DISPLAY_MS, null)
            }

            is IslandEvent.Charging -> TimeoutPolicy(CHARGING_DISPLAY_MS, CHARGING_DISPLAY_MS + CHARGING_LINGER_MS)

            is IslandEvent.Battery ->
                if (event.battery.isCritical) {
                    TimeoutPolicy(BATTERY_DISPLAY_MS * 2, BATTERY_DISPLAY_MS * 2 + BATTERY_LINGER_MS)
                } else {
                    TimeoutPolicy(BATTERY_DISPLAY_MS, BATTERY_DISPLAY_MS + BATTERY_LINGER_MS)
                }

            is IslandEvent.Bluetooth -> TimeoutPolicy(BLUETOOTH_DISPLAY_MS, BLUETOOTH_DISPLAY_MS + BLUETOOTH_LINGER_MS)

            is IslandEvent.Navigation -> TimeoutPolicy(NAVIGATION_DISPLAY_MS, null)

            is IslandEvent.Download ->
                if (event.download.completed) {
                    TimeoutPolicy(
                        DOWNLOAD_COMPLETE_DISPLAY_MS,
                        DOWNLOAD_COMPLETE_DISPLAY_MS + DOWNLOAD_COMPLETE_LINGER_MS,
                    )
                } else {
                    TimeoutPolicy(DOWNLOAD_DISPLAY_MS, null)
                }

            is IslandEvent.Alarm ->
                if (event.alarm.isRinging) {
                    TimeoutPolicy(collapseAfterMs = null, removeAfterMs = null)
                } else {
                    TimeoutPolicy(ALARM_UPCOMING_DISPLAY_MS, ALARM_UPCOMING_DISPLAY_MS + ALARM_UPCOMING_LINGER_MS)
                }

            is IslandEvent.System -> TimeoutPolicy(SYSTEM_DISPLAY_MS, SYSTEM_DISPLAY_MS + SYSTEM_LINGER_MS)

            is IslandEvent.Custom -> TimeoutPolicy(CUSTOM_DISPLAY_MS, CUSTOM_DISPLAY_MS + CUSTOM_LINGER_MS)
        }
    }
}
