package dev.island.data.events

import android.content.Context
import dev.island.R
import dev.island.domain.engine.IslandClock
import dev.island.domain.model.ActionLabelKey
import dev.island.domain.model.AlarmInfo
import dev.island.domain.model.BatteryInfo
import dev.island.domain.model.CallInfo
import dev.island.domain.model.CallState
import dev.island.domain.model.ChargingInfo
import dev.island.domain.model.ConnectedDeviceInfo
import dev.island.domain.model.ConnectedDeviceKind
import dev.island.domain.model.CustomInfo
import dev.island.domain.model.IslandAction
import dev.island.domain.model.IslandActionKind
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandEventMeta
import dev.island.domain.model.IslandEventType
import dev.island.domain.model.IslandIconKey
import dev.island.domain.model.IslandPriority
import dev.island.domain.model.MediaInfo
import dev.island.domain.model.PlaybackState
import dev.island.domain.model.PlugType
import dev.island.domain.model.StopwatchInfo
import dev.island.domain.model.SystemEventKind
import dev.island.domain.model.SystemInfo
import dev.island.domain.model.TimerInfo

/**
 * Builds island events from device/media/timer payloads.
 *
 * Titles and action labels come from resources here (the data layer owns Android), so the domain
 * and the renderer never need to know how a string is produced. Priorities are *proposals*:
 * [dev.island.domain.engine.PriorityResolver] recomputes them from the user's settings.
 */
class IslandEventFactories(
    private val context: Context,
    private val clock: IslandClock,
) {

    // region media

    fun media(info: MediaInfo): IslandEvent.Media {
        val playing = info.state == PlaybackState.PLAYING
        val title = info.title.ifBlank { context.getString(R.string.island_now_playing) }
        val subtitle = info.artist ?: info.album
        val actions = buildList {
            if (info.canSkipPrevious) add(mediaAction("prev", IslandActionKind.PREVIOUS, IslandIconKey.MUSIC, ActionLabelKey.PREVIOUS))
            if (info.canPlayPause) {
                add(
                    mediaAction(
                        "playpause",
                        IslandActionKind.PLAY_PAUSE,
                        IslandIconKey.MUSIC,
                        if (playing) ActionLabelKey.PAUSE else ActionLabelKey.PLAY,
                    ),
                )
            }
            if (info.canSkipNext) add(mediaAction("next", IslandActionKind.NEXT, IslandIconKey.MUSIC, ActionLabelKey.NEXT))
            add(mediaAction("dismiss", IslandActionKind.DISMISS, IslandIconKey.WARNING, ActionLabelKey.DISMISS))
        }
        return IslandEvent.Media(
            meta = meta(
                id = "media:${info.sourcePackage ?: "session"}",
                type = IslandEventType.MEDIA,
                priority = if (playing) IslandPriority.MEDIUM else IslandPriority.LOW,
                title = title,
                subtitle = subtitle,
                iconKey = IslandIconKey.MUSIC,
                progress = info.fractionAt(clock.elapsedMs()),
                actions = actions,
                expandable = true,
                persistent = true,
                sourcePackage = info.sourcePackage,
                sourceLabel = info.sourceLabel,
            ),
            media = info,
        )
    }

    private fun mediaAction(id: String, kind: IslandActionKind, icon: IslandIconKey, label: ActionLabelKey) =
        IslandAction(id = id, kind = kind, iconKey = icon, labelKey = label)

    // endregion

    // region timers

    fun timer(info: TimerInfo): IslandEvent.Timer {
        val remaining = formatDuration(info.remainingAt(clock.elapsedMs()))
        val actions = buildList {
            add(
                IslandAction(
                    id = "timer-toggle",
                    kind = IslandActionKind.TIMER_PAUSE_RESUME,
                    iconKey = IslandIconKey.TIMER,
                    labelKey = if (info.running) ActionLabelKey.PAUSE else ActionLabelKey.RESUME,
                    enabled = !info.finished,
                ),
            )
            add(
                IslandAction(
                    id = "timer-stop",
                    kind = IslandActionKind.TIMER_STOP,
                    iconKey = IslandIconKey.WARNING,
                    labelKey = ActionLabelKey.STOP,
                ),
            )
        }
        val title = when {
            info.finished -> context.getString(R.string.island_timer_complete)
            info.activeTimerCount > 1 -> "%s • %s".format(remaining, context.getString(R.string.island_timer))
            else -> remaining
        }
        return IslandEvent.Timer(
            meta = meta(
                id = "timer:${info.timerId}",
                type = IslandEventType.TIMER,
                priority = if (info.finished) IslandPriority.HIGH else IslandPriority.MEDIUM,
                title = title,
                subtitle = info.label,
                iconKey = IslandIconKey.TIMER,
                progress = info.remainingFractionAt(clock.elapsedMs()).let { 1f - it },
                actions = actions,
                expandable = true,
                persistent = !info.finished,
            ),
            timer = info,
        )
    }

    fun stopwatch(info: StopwatchInfo): IslandEvent.Stopwatch {
        val elapsed = formatDurationWithTenths(info.elapsedAt(clock.elapsedMs()))
        val actions = listOf(
            IslandAction(
                id = "sw-lap",
                kind = IslandActionKind.STOPWATCH_LAP,
                iconKey = IslandIconKey.STOPWATCH,
                labelKey = ActionLabelKey.LAP,
                enabled = info.running,
            ),
            IslandAction(
                id = "sw-toggle",
                kind = IslandActionKind.STOPWATCH_PAUSE_RESUME,
                iconKey = IslandIconKey.STOPWATCH,
                labelKey = if (info.running) ActionLabelKey.PAUSE else ActionLabelKey.RESUME,
            ),
            IslandAction(
                id = "sw-reset",
                kind = IslandActionKind.STOPWATCH_RESET,
                iconKey = IslandIconKey.WARNING,
                labelKey = ActionLabelKey.RESET,
            ),
        )
        return IslandEvent.Stopwatch(
            meta = meta(
                id = "stopwatch",
                type = IslandEventType.STOPWATCH,
                priority = if (info.running) IslandPriority.MEDIUM else IslandPriority.LOW,
                title = elapsed,
                subtitle = if (info.laps.isNotEmpty()) {
                    context.getString(R.string.stopwatch_lap_number, info.laps.size)
                } else {
                    context.getString(R.string.island_stopwatch)
                },
                iconKey = IslandIconKey.STOPWATCH,
                actions = actions,
                expandable = true,
                persistent = info.running,
            ),
            stopwatch = info,
        )
    }

    // endregion

    // region calls

    /** Returns null for [CallState.UNKNOWN] so no empty call pill is ever shown. */
    fun call(info: CallInfo): IslandEvent.Call? {
        if (info.state == CallState.UNKNOWN) return null
        val title = when (info.state) {
            CallState.INCOMING -> context.getString(R.string.island_incoming_call)
            CallState.ACTIVE -> context.getString(R.string.island_ongoing_call)
            CallState.HOLDING -> context.getString(R.string.island_call_on_hold)
            CallState.ENDED -> context.getString(R.string.island_call_ended)
            CallState.UNKNOWN -> return null
        }
        val subtitle = info.handle ?: when (info.state) {
            CallState.ACTIVE, CallState.HOLDING -> formatDuration(info.durationAt(clock.elapsedMs()))
            else -> null
        }
        val actions = buildList {
            if (info.canAnswer && info.state == CallState.INCOMING) {
                add(IslandAction("answer", IslandActionKind.ANSWER_CALL, IslandIconKey.CALL_INCOMING, ActionLabelKey.ANSWER))
            }
            if (info.canMute && info.state != CallState.INCOMING && info.state != CallState.ENDED) {
                add(
                    IslandAction(
                        "mute",
                        IslandActionKind.MUTE_CALL,
                        IslandIconKey.CALL_ACTIVE,
                        if (info.isMuted) ActionLabelKey.UNMUTE else ActionLabelKey.MUTE,
                    ),
                )
            }
            if (info.canEnd && info.state != CallState.ENDED) {
                add(IslandAction("end", IslandActionKind.END_CALL, IslandIconKey.CALL_ENDED, ActionLabelKey.END))
            }
        }
        return IslandEvent.Call(
            meta = meta(
                id = "call",
                type = IslandEventType.CALL,
                priority = when (info.state) {
                    CallState.INCOMING, CallState.ACTIVE -> IslandPriority.CRITICAL
                    CallState.HOLDING -> IslandPriority.HIGH
                    CallState.ENDED -> IslandPriority.LOW
                    CallState.UNKNOWN -> IslandPriority.LOW
                },
                title = title,
                subtitle = subtitle,
                iconKey = when (info.state) {
                    CallState.INCOMING -> IslandIconKey.CALL_INCOMING
                    CallState.ENDED -> IslandIconKey.CALL_ENDED
                    else -> IslandIconKey.CALL_ACTIVE
                },
                progress = null,
                actions = actions,
                expandable = true,
                persistent = info.state != CallState.ENDED,
                sourceLabel = info.handle,
            ),
            call = info,
        )
    }

    // endregion

    // region battery / charging

    fun chargingStarted(info: ChargingInfo): IslandEvent.Charging {
        val wattage = info.wattage
        val subtitle = buildString {
            append(context.getString(R.string.island_battery_percent, info.levelPercent))
            if (wattage != null) append(" • ").append(context.getString(R.string.island_power_watts, wattage))
        }
        return IslandEvent.Charging(
            meta = meta(
                id = "charging",
                type = IslandEventType.CHARGING,
                priority = IslandPriority.MEDIUM,
                title = if (info.plugged == PlugType.WIRELESS) {
                    context.getString(R.string.island_charging_wireless)
                } else {
                    context.getString(R.string.island_charging)
                },
                subtitle = subtitle,
                iconKey = IslandIconKey.BATTERY_CHARGING,
                progress = info.levelPercent / 100f,
                actions = listOf(dismissAction()),
                expandable = true,
                persistent = false,
            ),
            charging = info,
        )
    }

    fun battery(info: BatteryInfo): IslandEvent.Battery {
        val title = when {
            info.thresholdReachedPercent != null ->
                context.getString(R.string.island_battery_threshold, info.thresholdReachedPercent)

            info.isCritical -> context.getString(R.string.island_battery_critical)
            info.isLow -> context.getString(R.string.island_battery_low)
            info.isFull -> context.getString(R.string.island_battery_full)
            else -> context.getString(R.string.island_battery_percent, info.levelPercent)
        }
        return IslandEvent.Battery(
            meta = meta(
                id = "battery:${info.thresholdReachedPercent ?: if (info.isFull) "full" else if (info.isCritical) "critical" else "low"}",
                type = IslandEventType.BATTERY,
                priority = when {
                    info.isCritical -> IslandPriority.HIGH
                    info.isLow -> IslandPriority.HIGH
                    else -> IslandPriority.MEDIUM
                },
                title = title,
                subtitle = context.getString(R.string.island_battery_percent, info.levelPercent),
                iconKey = when {
                    info.isCharging -> IslandIconKey.BATTERY_CHARGING
                    info.isFull -> IslandIconKey.BATTERY_FULL
                    else -> IslandIconKey.BATTERY_LOW
                },
                progress = info.levelPercent / 100f,
                actions = listOf(dismissAction()),
                expandable = true,
                persistent = false,
            ),
            battery = info,
        )
    }

    // endregion

    // region devices

    fun device(device: ConnectedDeviceInfo): IslandEvent.Bluetooth {
        val label = device.name ?: context.getString(deviceKindLabel(device.kind))
        val title = context.getString(
            if (device.connected) R.string.island_device_connected else R.string.island_device_disconnected,
            label,
        )
        return IslandEvent.Bluetooth(
            meta = meta(
                id = "device:${device.kind}:${device.connected}",
                type = IslandEventType.BLUETOOTH,
                priority = IslandPriority.MEDIUM,
                title = title,
                subtitle = context.getString(deviceKindLabel(device.kind)),
                iconKey = when (device.kind) {
                    ConnectedDeviceKind.WIRED_HEADSET, ConnectedDeviceKind.BLUETOOTH_AUDIO -> IslandIconKey.HEADPHONES
                    ConnectedDeviceKind.WATCH -> IslandIconKey.WATCH
                    ConnectedDeviceKind.SPEAKER -> IslandIconKey.BLUETOOTH
                    ConnectedDeviceKind.CAR, ConnectedDeviceKind.OTHER -> IslandIconKey.BLUETOOTH
                },
                actions = listOf(dismissAction()),
                expandable = false,
                persistent = false,
            ),
            device = device,
        )
    }

    private fun deviceKindLabel(kind: ConnectedDeviceKind): Int = when (kind) {
        ConnectedDeviceKind.WIRED_HEADSET -> R.string.island_headphones
        ConnectedDeviceKind.BLUETOOTH_AUDIO -> R.string.island_headphones
        ConnectedDeviceKind.WATCH -> R.string.island_watch
        ConnectedDeviceKind.CAR -> R.string.island_car
        ConnectedDeviceKind.SPEAKER -> R.string.island_speaker
        ConnectedDeviceKind.OTHER -> R.string.island_bluetooth_device
    }

    fun alarm(info: AlarmInfo, formattedTime: String): IslandEvent.Alarm {
        val minutes = info.minutesUntil(clock.wallClockMs())
        val title = when {
            info.isRinging -> context.getString(R.string.island_alarm)
            minutes <= 0 -> formattedTime
            else -> context.getString(R.string.island_alarm_in, minutes.toInt())
        }
        return IslandEvent.Alarm(
            meta = meta(
                id = "alarm:${info.triggerAtMs}",
                type = IslandEventType.ALARM,
                priority = if (info.isRinging) IslandPriority.CRITICAL else IslandPriority.MEDIUM,
                title = title,
                subtitle = context.getString(R.string.island_alarm_at, formattedTime),
                iconKey = IslandIconKey.ALARM,
                actions = listOf(dismissAction()),
                expandable = false,
                persistent = info.isRinging,
                sourcePackage = info.showIntentPackage,
            ),
            alarm = info,
        )
    }

    // endregion

    // region system / custom

    fun system(kind: SystemEventKind, title: String, subtitle: String? = null): IslandEvent.System =
        IslandEvent.System(
            meta = meta(
                id = "system:${kind.name.lowercase()}",
                type = IslandEventType.SYSTEM,
                priority = IslandPriority.LOW,
                title = title,
                subtitle = subtitle,
                iconKey = if (kind == SystemEventKind.PERMISSION_REVOKED) IslandIconKey.WARNING else IslandIconKey.INFO,
                actions = listOf(dismissAction()),
                expandable = false,
                persistent = false,
            ),
            system = SystemInfo(kind = kind),
        )

    /**
     * Public custom-event entry point:
     * ```
     * IslandEvent.Custom(title = "Workout", subtitle = "Set 3 of 5", progress = 0.6f)
     * ```
     * Companion apps can reuse the same shape through a documented local broadcast interface.
     */
    fun custom(
        title: String,
        subtitle: String? = null,
        progress: Float? = null,
        iconKey: IslandIconKey = IslandIconKey.CUSTOM,
        accentArgb: Long? = null,
        durationMs: Long? = null,
        category: String? = null,
        sourcePackage: String? = null,
    ): IslandEvent.Custom {
        val now = clock.wallClockMs()
        return IslandEvent.Custom(
            meta = meta(
                id = "custom:${category ?: title}:$now",
                type = IslandEventType.CUSTOM,
                priority = IslandPriority.MEDIUM,
                title = title,
                subtitle = subtitle,
                iconKey = iconKey,
                accentArgb = accentArgb,
                progress = progress?.coerceIn(0f, 1f),
                actions = listOf(dismissAction()),
                expandable = subtitle != null || progress != null,
                persistent = durationMs == null,
                sourcePackage = sourcePackage,
                createdAt = now,
                expiresAt = durationMs?.let { now + it },
            ),
            custom = CustomInfo(category = category),
        )
    }

    // endregion

    private fun dismissAction() =
        IslandAction(id = "dismiss", kind = IslandActionKind.DISMISS, iconKey = IslandIconKey.WARNING, labelKey = ActionLabelKey.DISMISS)

    private fun meta(
        id: String,
        type: IslandEventType,
        priority: IslandPriority,
        title: String,
        subtitle: String? = null,
        iconKey: IslandIconKey = IslandIconKey.INFO,
        accentArgb: Long? = null,
        progress: Float? = null,
        actions: List<IslandAction> = emptyList(),
        expandable: Boolean = true,
        persistent: Boolean = false,
        sourcePackage: String? = null,
        sourceLabel: String? = null,
        createdAt: Long = clock.wallClockMs(),
        expiresAt: Long? = null,
    ) = IslandEventMeta(
        id = id,
        type = type,
        priority = priority,
        createdAt = createdAt,
        expiresAt = expiresAt,
        title = title,
        subtitle = subtitle,
        iconKey = iconKey,
        accentArgb = accentArgb,
        progress = progress,
        actions = actions,
        expandable = expandable,
        persistent = persistent,
        sourcePackage = sourcePackage,
        sourceLabel = sourceLabel,
        coalesceKey = id,
    )

    companion object {
        fun formatDuration(ms: Long): String {
            val safe = ms.coerceAtLeast(0L)
            val totalSeconds = (safe + 999L) / 1000L
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
        }

        fun formatDurationWithTenths(ms: Long): String {
            val safe = ms.coerceAtLeast(0L)
            val tenths = (safe % 1000L) / 100L
            val totalSeconds = safe / 1000L
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                "%d:%02d:%02d.%d".format(hours, minutes, seconds, tenths)
            } else {
                "%02d:%02d.%d".format(minutes, seconds, tenths)
            }
        }
    }
}
