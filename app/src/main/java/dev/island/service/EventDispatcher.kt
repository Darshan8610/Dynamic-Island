package dev.island.service

import dev.island.core.logging.IslandLogger
import dev.island.data.device.AlarmMonitor
import dev.island.data.device.BatteryMonitor
import dev.island.data.device.BluetoothMonitor
import dev.island.data.device.CallStateMonitor
import dev.island.data.device.DeviceStateRepositoryImpl
import dev.island.data.events.IslandEventFactories
import dev.island.data.media.MediaSessionRepositoryImpl
import dev.island.data.notifications.NotificationRepository
import dev.island.data.timers.TimerEngine
import dev.island.domain.engine.IslandClock
import dev.island.domain.engine.IslandEngine
import dev.island.domain.model.BatteryInfo
import dev.island.domain.model.ChargingInfo
import dev.island.domain.model.ConnectedDeviceInfo
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.PlaybackState
import dev.island.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Wires every event source into [IslandEngine].
 *
 * ```
 * NotificationListenerService ─┐
 * MediaSessionManager         ─┤
 * TimerEngine                 ─┼→ EventDispatcher → IslandEngine → IslandUiState → overlay
 * Battery / Bluetooth / Call  ─┤
 * AlarmManager                ─┘
 * ```
 *
 * The dispatcher owns no UI logic and no window logic: it only decides *when a source is worth
 * listening to*, based on the user's settings, and translates state changes into events. Every
 * source is collected, never polled.
 */
class EventDispatcher(
    private val scope: CoroutineScope,
    private val logger: IslandLogger,
    private val clock: IslandClock,
    private val engine: IslandEngine,
    private val settingsRepository: SettingsRepository,
    private val deviceStateRepository: DeviceStateRepositoryImpl,
    private val mediaRepository: MediaSessionRepositoryImpl,
    private val timerRepository: TimerEngine,
    private val batteryMonitor: BatteryMonitor,
    private val bluetoothMonitor: BluetoothMonitor,
    private val callStateMonitor: CallStateMonitor,
    private val alarmMonitor: AlarmMonitor,
    private val notificationRepository: NotificationRepository,
    private val factories: IslandEventFactories,
) {

    private val jobs = mutableListOf<Job>()
    private var settings: IslandSettings = IslandSettings.Default
    private var running = false

    // Source state used to emit edges rather than levels (no repeated identical events).
    private var wasCharging = false
    private var lastAnnouncedLevel = -1
    private var announcedThreshold = -1
    private var announcedFull = false
    private var lastDeviceEvent: ConnectedDeviceInfo? = null
    private var lastDeviceEventAtMs = 0L
    private var activeMediaEventId: String? = null
    private var activeTimerIds = mutableSetOf<String>()
    private var stopwatchActive = false
    private var alarmJob: Job? = null
    private var activeAlarmId: String? = null

    val isRunning: Boolean get() = running

    fun start() {
        if (running) return
        running = true
        logger.i(TAG, "dispatcher starting")

        deviceStateRepository.start()
        timerRepository.start()

        notificationRepository.onEvent = { event -> engine.submit(event) }
        notificationRepository.onEventRemoved = { key -> engine.removeByCoalesceKey(key) }

        jobs += scope.launch {
            settingsRepository.settings.collectLatest { next ->
                val previous = settings
                settings = next
                applySettings(previous, next)
            }
        }
        jobs += observeMedia()
        jobs += observeTimers()
        jobs += observeStopwatch()
        jobs += observeBattery()
        jobs += observeDevices()
        jobs += observeCalls()
        jobs += observeAlarms()
    }

    fun stop() {
        if (!running) return
        running = false
        alarmJob?.cancel()
        jobs.forEach { it.cancel() }
        jobs.clear()
        notificationRepository.onEvent = null
        notificationRepository.onEventRemoved = null
        mediaRepository.stop()
        callStateMonitor.stop()
        deviceStateRepository.stop()
        logger.i(TAG, "dispatcher stopped")
    }

    /** Re-applies source gating after a settings change; idempotent. */
    fun applySettings(previous: IslandSettings, next: IslandSettings) {
        if (next.media.enabled && !mediaRunning) {
            mediaRepository.start()
            mediaRunning = true
        } else if (!next.media.enabled && mediaRunning) {
            mediaRepository.stop()
            mediaRunning = false
            activeMediaEventId?.let { engine.remove(it) }
            activeMediaEventId = null
        }

        if (callsEnabled(next) && !callMonitorRunning) {
            callStateMonitor.start()
            callMonitorRunning = true
        } else if (!callsEnabled(next) && callMonitorRunning) {
            callStateMonitor.stop()
            callMonitorRunning = false
            engine.remove(CALL_EVENT_ID)
        }

        // Media access is only possible with notification access; retry when it is granted later.
        if (next.notifications.enabled && previous != next) {
            mediaRepository.refreshSessions("settings changed")
        }
        if (next.devices.alarmEvents != previous.devices.alarmEvents ||
            next.devices.alarmLeadTimeMinutes != previous.devices.alarmLeadTimeMinutes
        ) {
            rescheduleAlarm()
        }
    }

    private var mediaRunning = false
    private var callMonitorRunning = false

    private fun callsEnabled(settings: IslandSettings): Boolean =
        settings.calls.showIncoming || settings.calls.showOngoing

    // region sources

    private fun observeMedia(): Job = scope.launch {
        mediaRepository.media.collect { info ->
            if (!settings.media.enabled || info == null || info.state == PlaybackState.STOPPED) {
                activeMediaEventId?.let { engine.remove(it) }
                activeMediaEventId = null
                return@collect
            }
            val event = factories.media(info)
            activeMediaEventId = event.id
            engine.submit(event)
        }
    }

    private fun observeTimers(): Job = scope.launch {
        timerRepository.timers.collect { timers ->
            val currentIds = timers.map { it.timerId }.toMutableSet()
            // Timers the user stopped disappear from the island immediately.
            (activeTimerIds - currentIds).forEach { engine.remove("timer:$it") }
            activeTimerIds = currentIds
            timers.forEach { timer -> engine.submit(factories.timer(timer)) }
        }
    }

    private fun observeStopwatch(): Job = scope.launch {
        timerRepository.stopwatch.collect { info ->
            if (info == null) {
                if (stopwatchActive) engine.remove(STOPWATCH_EVENT_ID)
                stopwatchActive = false
                return@collect
            }
            stopwatchActive = true
            engine.submit(factories.stopwatch(info))
        }
    }

    private fun observeBattery(): Job = scope.launch {
        batteryMonitor.snapshots.collect { charging -> onBatteryChanged(charging) }
    }

    private fun onBatteryChanged(info: ChargingInfo) {
        val batterySettings = settings.battery
        val level = info.levelPercent

        if (info.isCharging && !wasCharging) {
            wasCharging = true
            announcedFull = false
            announcedThreshold = -1
            if (batterySettings.showCharging) engine.submit(factories.chargingStarted(info))
        } else if (!info.isCharging && wasCharging) {
            wasCharging = false
            announcedFull = false
            announcedThreshold = -1
            // Unplugging is not worth an animation: the charging event simply leaves the island.
            engine.remove(CHARGING_EVENT_ID)
        }

        if (!info.isCharging && level <= batterySettings.lowBatteryPercent && level != lastAnnouncedLevel) {
            lastAnnouncedLevel = level
            if (batterySettings.showLowBattery) {
                engine.submit(
                    factories.battery(
                        BatteryInfo(
                            levelPercent = level,
                            isCharging = false,
                            plugged = info.plugged,
                            isLow = level > CRITICAL_BATTERY_PERCENT,
                            isCritical = level <= CRITICAL_BATTERY_PERCENT,
                        ),
                    ),
                )
            }
        }

        if (info.isCharging) {
            if (batterySettings.thresholdEnabled &&
                level >= batterySettings.thresholdPercent &&
                announcedThreshold < batterySettings.thresholdPercent
            ) {
                announcedThreshold = batterySettings.thresholdPercent
                engine.submit(
                    factories.battery(
                        BatteryInfo(
                            levelPercent = level,
                            isCharging = true,
                            plugged = info.plugged,
                            thresholdReachedPercent = batterySettings.thresholdPercent,
                        ),
                    ),
                )
            }
            if (level >= 100 && !announcedFull) {
                announcedFull = true
                if (batterySettings.showFullCharge) {
                    engine.submit(
                        factories.battery(
                            BatteryInfo(levelPercent = level, isCharging = true, plugged = info.plugged, isFull = true),
                        ),
                    )
                }
            }
        }
    }

    private fun observeDevices(): Job = scope.launch {
        bluetoothMonitor.connections.collect { device ->
            // Identical connect/disconnect pairs from flaky headsets are collapsed.
            if (device == lastDeviceEvent && clock.elapsedMs() - lastDeviceEventAtMs < DEVICE_DEDUPE_MS) {
                logger.d(TAG, "duplicate device event ignored")
                return@collect
            }
            lastDeviceEvent = device
            lastDeviceEventAtMs = clock.elapsedMs()
            engine.submit(factories.device(device))
        }
    }

    private fun observeCalls(): Job = scope.launch {
        callStateMonitor.call.collect { call ->
            val event = factories.call(call)
            if (event == null) {
                engine.remove(CALL_EVENT_ID)
                return@collect
            }
            engine.submit(event)
        }
    }

    private fun observeAlarms(): Job = scope.launch {
        alarmMonitor.changes.collect { rescheduleAlarm() }
    }

    /**
     * Schedules the "Alarm in N min" event exactly once, at the user's lead time. A coroutine
     * delay is enough because Island is a foreground service while this is relevant; if the
     * process is not running, no alert is shown (documented limitation — Island never registers
     * wake-ups it does not need).
     */
    private fun rescheduleAlarm() {
        alarmJob?.cancel()
        if (!settings.devices.alarmEvents) {
            activeAlarmId?.let { engine.remove(it) }
            activeAlarmId = null
            return
        }
        val info = alarmMonitor.current()
        if (info == null) {
            activeAlarmId?.let { engine.remove(it) }
            activeAlarmId = null
            return
        }
        val leadMs = settings.devices.alarmLeadTimeMinutes * 60_000L
        val showAt = info.triggerAtMs - leadMs
        val delayMs = showAt - clock.wallClockMs()
        val eventId = "alarm:${info.triggerAtMs}"
        activeAlarmId?.let { if (it != eventId) engine.remove(it) }
        activeAlarmId = eventId

        alarmJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            val current = alarmMonitor.current() ?: return@launch
            engine.submit(factories.alarm(current, alarmMonitor.formatTriggerTime(current)))
        }
    }

    // endregion

    /** Used by demo mode and by the "Test island" button. */
    fun submitCustom(event: IslandEvent) = engine.submit(event)

    companion object {
        private const val TAG = "EventDispatcher"
        private const val CHARGING_EVENT_ID = "charging"
        private const val CALL_EVENT_ID = "call"
        private const val STOPWATCH_EVENT_ID = "stopwatch"
        private const val DEVICE_DEDUPE_MS = 1_500L
        private const val CRITICAL_BATTERY_PERCENT = 5
    }
}
