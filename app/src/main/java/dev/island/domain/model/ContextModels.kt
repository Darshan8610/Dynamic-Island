package dev.island.domain.model

/** Turn-by-turn manoeuvre vocabulary for navigation events. */
enum class ManeuverKind {
    STRAIGHT,
    LEFT,
    RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    SHARP_LEFT,
    SHARP_RIGHT,
    U_TURN,
    MERGE,
    FORK,
    ROUNDABOUT,
    EXIT,
    ARRIVE,
    UNKNOWN,
}

/**
 * Navigation event. Android offers no public API to read another app's turn-by-turn state,
 * so this is fed either from a navigation app's own ongoing notification (parsed by
 * [dev.island.data.notifications.NotificationEventFactory]) or from a future
 * [dev.island.domain.repository.NavigationProvider] implementation. Island never reads
 * screen pixels and never uses accessibility scraping.
 */
data class NavigationInfo(
    val maneuver: ManeuverKind = ManeuverKind.UNKNOWN,
    val distanceMeters: Int? = null,
    val streetName: String? = null,
    val etaMinutes: Int? = null,
    val remainingDistanceMeters: Int? = null,
    val provider: String? = null,
) {
    /** "500 m" / "1.2 km" — localised by the UI layer, distance kept numeric here. */
    val isMetricShort: Boolean get() = (distanceMeters ?: Int.MAX_VALUE) < 1000
}

/** Download / file-transfer progress, derived only from notifications that publish progress. */
data class DownloadInfo(
    val fileName: String? = null,
    val percent: Int? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val bytesPerSecond: Long? = null,
    val indeterminate: Boolean = false,
    val completed: Boolean = false,
) {
    val fraction: Float? get() = percent?.let { (it / 100f).coerceIn(0f, 1f) }
}

/** System alarm clock state (`AlarmManager.getNextAlarmClock`). */
data class AlarmInfo(
    val label: String? = null,
    val triggerAtMs: Long = 0L,
    val showIntentPackage: String? = null,
    val isRinging: Boolean = false,
    val canSnooze: Boolean = false,
) {
    fun minutesUntil(nowMs: Long): Long =
        ((triggerAtMs - nowMs) / 60_000L).coerceAtLeast(0L)
}

enum class SystemEventKind {
    DO_NOT_DISTURB_ON,
    DO_NOT_DISTURB_OFF,
    SERVICE_STARTED,
    SERVICE_RESTARTED,
    PERMISSION_REVOKED,
    PERMISSION_GRANTED,
    SCREEN_OFF,
    CONFIGURATION_CHANGED,
    DEMO,
    GENERIC,
}

/** Catch-all for device state changes that are not covered by a dedicated event type. */
data class SystemInfo(
    val kind: SystemEventKind = SystemEventKind.GENERIC,
    val detail: String? = null,
    val value: Float? = null,
)

/**
 * Payload for [IslandEvent.Custom] — the extension point for future companion apps
 * (fitness, smart home, delivery tracking, coding tools…).
 */
data class CustomInfo(
    val category: String? = null,
    val iconHint: String? = null,
    val extras: Map<String, String> = emptyMap(),
)
