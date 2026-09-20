package dev.island.core.permissions

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.island.core.logging.IslandLogger
import dev.island.domain.model.IslandFeature
import dev.island.domain.model.PermissionKey
import dev.island.domain.model.PermissionStatus
import dev.island.domain.repository.PermissionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Runtime capability checks. Every optional capability degrades independently:
 *
 * - no overlay permission      → the dashboard and settings still work, the island does not render
 * - no notification access     → media, timers, charging, Bluetooth and call islands still work
 * - no BLUETOOTH_CONNECT       → device events fire with a generic label instead of a device name
 * - no READ_PHONE_STATE        → the call island is disabled, everything else is untouched
 * - no exact alarm permission  → timers complete with an inexact alarm while the screen is off
 */
class AndroidPermissionRepository(
    private val context: Context,
    private val logger: IslandLogger,
) : PermissionRepository {

    private val _statuses = MutableStateFlow<List<PermissionStatus>>(emptyList())
    private val state: StateFlow<List<PermissionStatus>> = _statuses

    override val statuses = state

    override fun refresh() {
        _statuses.value = snapshot()
    }

    fun snapshot(): List<PermissionStatus> = listOf(
        PermissionStatus(
            key = PermissionKey.OVERLAY,
            granted = canDrawOverlays(),
            required = true,
            affectsFeature = IslandFeature.OVERLAY,
        ),
        PermissionStatus(
            key = PermissionKey.NOTIFICATION_LISTENER,
            granted = isNotificationListenerEnabled(),
            required = false,
            affectsFeature = IslandFeature.NOTIFICATIONS,
        ),
        PermissionStatus(
            key = PermissionKey.POST_NOTIFICATIONS,
            granted = areNotificationsEnabled(),
            required = false,
            affectsFeature = IslandFeature.TIMERS,
        ),
        PermissionStatus(
            key = PermissionKey.BLUETOOTH_CONNECT,
            granted = hasBluetoothConnect(),
            required = false,
            affectsFeature = IslandFeature.BLUETOOTH,
        ),
        PermissionStatus(
            key = PermissionKey.READ_PHONE_STATE,
            granted = hasReadPhoneState(),
            required = false,
            affectsFeature = IslandFeature.CALLS,
        ),
        PermissionStatus(
            key = PermissionKey.ANSWER_PHONE_CALLS,
            granted = hasAnswerPhoneCalls(),
            required = false,
            affectsFeature = IslandFeature.CALLS,
        ),
        PermissionStatus(
            key = PermissionKey.EXACT_ALARM,
            granted = canScheduleExactAlarms(),
            required = false,
            affectsFeature = IslandFeature.ALARMS,
        ),
        PermissionStatus(
            key = PermissionKey.BATTERY_OPTIMIZATION,
            granted = isIgnoringBatteryOptimizations(),
            required = false,
            affectsFeature = IslandFeature.OVERLAY,
            severity = PermissionStatus.Severity.WARNING,
        ),
    )

    fun canDrawOverlays(): Boolean = runCatching { Settings.canDrawOverlays(context) }
        .onFailure { logger.w(TAG, "canDrawOverlays failed", it) }
        .getOrDefault(false)

    /**
     * Whether the user enabled Island in "Notification access". Read from the secure setting the
     * same way the platform does — no AccessibilityService and no hidden API involved.
     */
    fun isNotificationListenerEnabled(): Boolean = runCatching {
        val flat = ComponentName(context, NOTIFICATION_LISTENER_CLASS).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return@runCatching false
        enabled.split(':').any { it.equals(flat, ignoreCase = true) }
    }.onFailure { logger.w(TAG, "listener state read failed", it) }.getOrDefault(false)

    fun areNotificationsEnabled(): Boolean = runCatching {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.areNotificationsEnabled() ?: false
    }.getOrDefault(false)

    fun hasBluetoothConnect(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)

    fun hasReadPhoneState(): Boolean = hasPermission(android.Manifest.permission.READ_PHONE_STATE)

    fun hasAnswerPhoneCalls(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            hasPermission(android.Manifest.permission.ANSWER_PHONE_CALLS)

    fun canScheduleExactAlarms(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            true
        } else {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.canScheduleExactAlarms() ?: false
        }
    }.getOrDefault(false)

    fun isIgnoringBatteryOptimizations(): Boolean = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }.getOrDefault(false)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "Permissions"
        const val NOTIFICATION_LISTENER_CLASS = "dev.island.data.notifications.IslandNotificationListener"
    }
}
