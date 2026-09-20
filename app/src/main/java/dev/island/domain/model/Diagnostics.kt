package dev.island.domain.model

/** Every runtime capability Island may need. Each one degrades independently. */
enum class PermissionKey {
    OVERLAY,
    NOTIFICATION_LISTENER,
    POST_NOTIFICATIONS,
    BLUETOOTH_CONNECT,
    READ_PHONE_STATE,
    EXACT_ALARM,
    BATTERY_OPTIMIZATION,
}

/**
 * One row of the diagnostics screen. [severity] distinguishes "required for the island to
 * render" from "optional feature disabled".
 */
data class PermissionStatus(
    val key: PermissionKey,
    val granted: Boolean,
    val required: Boolean,
    val affectsFeature: IslandFeature,
    val severity: Severity = if (required) Severity.ERROR else Severity.WARNING,
) {
    enum class Severity { OK, WARNING, ERROR }
}

enum class IslandFeature {
    OVERLAY,
    NOTIFICATIONS,
    MEDIA,
    CALLS,
    BLUETOOTH,
    TIMERS,
    ALARMS,
    HISTORY,
}

/** Snapshot for the diagnostics + performance screens. Computed on demand, never polled. */
data class DiagnosticsReport(
    val permissions: List<PermissionStatus>,
    val islandEnabled: Boolean,
    val serviceRunning: Boolean,
    val overlayAttached: Boolean,
    val listenerConnected: Boolean,
    val mediaSessionAvailable: Boolean,
    val phase: IslandPhase,
    val activeEvents: Int,
    val lastEventAtMs: Long?,
    val uptimeMs: Long,
    val manufacturer: String,
    val model: String,
    val sdkInt: Int,
    val isIgnoringBatteryOptimizations: Boolean,
) {
    val blocking: List<PermissionStatus> get() = permissions.filter { !it.granted && it.severity == PermissionStatus.Severity.ERROR }
    val warnings: List<PermissionStatus> get() = permissions.filter { !it.granted && it.severity == PermissionStatus.Severity.WARNING }
    val healthy: Boolean get() = blocking.isEmpty()
}
