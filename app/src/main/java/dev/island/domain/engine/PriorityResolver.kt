package dev.island.domain.engine

import dev.island.domain.model.AppNotificationMode
import dev.island.domain.model.BatteryInfo
import dev.island.domain.model.CallState
import dev.island.domain.model.ConnectedDeviceKind
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandPriority
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.NotificationImportance
import dev.island.domain.model.PlaybackState

/**
 * Decides how loudly an event may announce itself.
 *
 * CRITICAL — incoming/active call, ringing alarm, critical battery, emergency notifications
 * HIGH     — navigation, completed timer, important notification, low battery
 * MEDIUM   — media, charging, Bluetooth/audio device, download, upcoming alarm
 * LOW      — generic notification, background status, informational system events
 * BACKGROUND — channel importance MIN / user filtered out; normally not shown at all
 *
 * Pure function: identical inputs always produce identical priorities (see PriorityResolverTest).
 */
object PriorityResolver {

    fun resolve(event: IslandEvent, settings: IslandSettings): IslandPriority = when (event) {
        is IslandEvent.Call -> resolveCall(event)
        is IslandEvent.Alarm -> if (event.alarm.isRinging) IslandPriority.CRITICAL else IslandPriority.MEDIUM
        is IslandEvent.Timer -> resolveTimer(event)
        is IslandEvent.Stopwatch -> if (event.stopwatch.running) IslandPriority.MEDIUM else IslandPriority.LOW
        is IslandEvent.Media -> resolveMedia(event)
        is IslandEvent.Charging -> resolveCharging(event)
        is IslandEvent.Battery -> resolveBattery(event, settings)
        is IslandEvent.Bluetooth -> IslandPriority.MEDIUM
        is IslandEvent.Navigation -> IslandPriority.HIGH
        is IslandEvent.Download -> if (event.download.completed) IslandPriority.LOW else IslandPriority.MEDIUM
        is IslandEvent.Notification -> resolveNotification(event, settings)
        is IslandEvent.System -> IslandPriority.LOW
        is IslandEvent.Custom -> event.meta.priority
    }

    /**
     * Whether the event may be shown at all, given user filters and Do Not Disturb.
     * DND never hides CRITICAL events when [IslandSettings.behavior] allows them; Island never
     * bypasses the system's own notification suppression — it only decides what *it* renders.
     */
    fun shouldShow(
        event: IslandEvent,
        priority: IslandPriority,
        settings: IslandSettings,
        doNotDisturb: Boolean,
    ): Boolean {
        if (priority == IslandPriority.BACKGROUND) return false

        if (doNotDisturb && settings.behavior.followSystemDnd) {
            val allowCritical = settings.behavior.allowCriticalDuringDnd
            val threshold = if (allowCritical) IslandPriority.HIGH else IslandPriority.CRITICAL
            if (!priority.atLeast(threshold)) return false
        }

        return when (event) {
            is IslandEvent.Notification -> notificationAllowed(event, settings)
            is IslandEvent.Media -> settings.media.enabled
            is IslandEvent.Call -> callAllowed(event, settings)
            is IslandEvent.Charging -> settings.battery.showCharging || !event.charging.isCharging
            is IslandEvent.Battery -> batteryAllowed(event, settings)
            is IslandEvent.Bluetooth -> bluetoothAllowed(event, settings)
            is IslandEvent.Alarm -> settings.devices.alarmEvents
            is IslandEvent.Timer,
            is IslandEvent.Stopwatch,
            is IslandEvent.Navigation,
            is IslandEvent.Download,
            is IslandEvent.System,
            is IslandEvent.Custom,
            -> true
        }
    }

    private fun resolveCall(event: IslandEvent.Call): IslandPriority = when (event.call.state) {
        CallState.INCOMING -> IslandPriority.CRITICAL
        CallState.ACTIVE -> IslandPriority.CRITICAL
        CallState.HOLDING -> IslandPriority.HIGH
        CallState.ENDED -> IslandPriority.LOW
        CallState.UNKNOWN -> IslandPriority.MEDIUM
    }

    private fun resolveTimer(event: IslandEvent.Timer): IslandPriority = when {
        event.timer.finished -> IslandPriority.HIGH
        event.timer.running -> IslandPriority.MEDIUM
        else -> IslandPriority.LOW
    }

    private fun resolveMedia(event: IslandEvent.Media): IslandPriority = when (event.media.state) {
        PlaybackState.PLAYING -> IslandPriority.MEDIUM
        PlaybackState.BUFFERING -> IslandPriority.MEDIUM
        PlaybackState.PAUSED -> IslandPriority.LOW
        PlaybackState.STOPPED -> IslandPriority.BACKGROUND
        PlaybackState.UNKNOWN -> IslandPriority.LOW
    }

    private fun resolveCharging(event: IslandEvent.Charging): IslandPriority = when {
        event.charging.levelPercent <= 5 -> IslandPriority.HIGH
        event.charging.isCharging -> IslandPriority.MEDIUM
        else -> IslandPriority.LOW
    }

    private fun resolveBattery(event: IslandEvent.Battery, settings: IslandSettings): IslandPriority {
        val battery: BatteryInfo = event.battery
        return when {
            battery.isCritical -> IslandPriority.HIGH
            battery.isLow && settings.battery.showLowBattery -> IslandPriority.HIGH
            battery.thresholdReachedPercent != null -> IslandPriority.MEDIUM
            battery.isFull -> IslandPriority.LOW
            else -> IslandPriority.LOW
        }
    }

    private fun resolveNotification(event: IslandEvent.Notification, settings: IslandSettings): IslandPriority {
        val n = event.notification
        val byImportance = when (n.importance) {
            NotificationImportance.URGENT -> IslandPriority.HIGH
            NotificationImportance.HIGH -> IslandPriority.HIGH
            NotificationImportance.DEFAULT -> IslandPriority.LOW
            NotificationImportance.LOW -> IslandPriority.LOW
            NotificationImportance.MIN -> IslandPriority.BACKGROUND
            NotificationImportance.NONE -> IslandPriority.BACKGROUND
        }
        // A silenced channel is never promoted, whatever its category claims.
        if (byImportance == IslandPriority.BACKGROUND) return byImportance

        // Android's own categories refine the band (call, navigation, progress…).
        val byCategory = when (n.category) {
            CATEGORY_CALL -> IslandPriority.CRITICAL
            CATEGORY_ALARM -> IslandPriority.HIGH
            CATEGORY_NAVIGATION -> IslandPriority.HIGH
            CATEGORY_TRANSPORT -> IslandPriority.MEDIUM
            CATEGORY_PROGRESS -> IslandPriority.MEDIUM
            CATEGORY_SYSTEM, CATEGORY_SERVICE, CATEGORY_REMINDER -> IslandPriority.MEDIUM
            CATEGORY_ERROR, CATEGORY_CRASH -> IslandPriority.HIGH
            else -> null
        }
        val byStyle = if (n.isCallStyle) IslandPriority.CRITICAL else null

        return listOfNotNull(byImportance, byCategory, byStyle).maxByOrNull { it.rank } ?: byImportance
    }

    private fun notificationAllowed(event: IslandEvent.Notification, settings: IslandSettings): Boolean {
        val n = event.notification
        if (!settings.notifications.enabled) return false
        if (n.importance.value < settings.notifications.minImportance.value) return false
        if (n.isOngoing && !settings.notifications.showOngoingNotifications && !n.isForegroundService) return false
        if (n.isForegroundService && !settings.notifications.showOngoingNotifications) return false
        return when (settings.notifications.modeFor(n.packageName)) {
            AppNotificationMode.NEVER -> false
            AppNotificationMode.IMPORTANT_ONLY -> n.importance.atLeastHigh() || n.isCallStyle
            AppNotificationMode.ALWAYS,
            AppNotificationMode.ICON_ONLY,
            AppNotificationMode.FULL_PREVIEW,
            -> true
        }
    }

    private fun callAllowed(event: IslandEvent.Call, settings: IslandSettings): Boolean = when (event.call.state) {
        CallState.INCOMING -> settings.calls.showIncoming
        CallState.ACTIVE, CallState.HOLDING -> settings.calls.showOngoing
        CallState.ENDED -> settings.calls.showOngoing
        CallState.UNKNOWN -> false
    }

    private fun batteryAllowed(event: IslandEvent.Battery, settings: IslandSettings): Boolean {
        val b = event.battery
        return when {
            b.isCritical || b.isLow -> settings.battery.showLowBattery
            b.isFull -> settings.battery.showFullCharge
            b.thresholdReachedPercent != null -> settings.battery.thresholdEnabled
            else -> true
        }
    }

    private fun bluetoothAllowed(event: IslandEvent.Bluetooth, settings: IslandSettings): Boolean {
        val kind = event.device.kind
        val featureEnabled = when (kind) {
            ConnectedDeviceKind.WIRED_HEADSET -> settings.devices.wiredHeadsetEvents
            ConnectedDeviceKind.CAR -> settings.devices.carEvents
            ConnectedDeviceKind.WATCH -> settings.devices.bluetoothEvents
            ConnectedDeviceKind.BLUETOOTH_AUDIO,
            ConnectedDeviceKind.SPEAKER,
            ConnectedDeviceKind.OTHER,
            -> settings.devices.bluetoothEvents
        }
        return featureEnabled && kind in settings.devices.allowedKinds
    }

    private fun NotificationImportance.atLeastHigh(): Boolean =
        this == NotificationImportance.HIGH || this == NotificationImportance.URGENT

    // Android Notification.CATEGORY_* constants mirrored here to keep the domain JVM-testable.
    const val CATEGORY_CALL = "call"
    const val CATEGORY_ALARM = "alarm"
    const val CATEGORY_NAVIGATION = "navigation"
    const val CATEGORY_TRANSPORT = "transport"
    const val CATEGORY_PROGRESS = "progress"
    const val CATEGORY_SYSTEM = "sys"
    const val CATEGORY_SERVICE = "service"
    const val CATEGORY_REMINDER = "reminder"
    const val CATEGORY_ERROR = "err"
    const val CATEGORY_CRASH = "crash"
}
