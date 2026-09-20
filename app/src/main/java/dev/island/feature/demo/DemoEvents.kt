package dev.island.feature.demo

import dev.island.core.AppGraph
import dev.island.domain.engine.IslandClock
import dev.island.domain.model.AlarmInfo
import dev.island.domain.model.BatteryInfo
import dev.island.domain.model.CallInfo
import dev.island.domain.model.CallState
import dev.island.domain.model.ChargingInfo
import dev.island.domain.model.ConnectedDeviceInfo
import dev.island.domain.model.ConnectedDeviceKind
import dev.island.domain.model.DownloadInfo
import dev.island.domain.model.IslandAction
import dev.island.domain.model.IslandActionKind
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandEventMeta
import dev.island.domain.model.IslandEventType
import dev.island.domain.model.IslandIconKey
import dev.island.domain.model.IslandPriority
import dev.island.domain.model.ManeuverKind
import dev.island.domain.model.MediaInfo
import dev.island.domain.model.NavigationInfo
import dev.island.domain.model.NotificationImportance
import dev.island.domain.model.NotificationInfo
import dev.island.domain.model.PlaybackState
import dev.island.domain.model.PlugType
import dev.island.domain.model.StopwatchInfo
import dev.island.domain.model.TimerInfo

/**
 * Sample events for Demo mode, onboarding's "test the island" step and the in-app preview.
 *
 * These are ordinary [IslandEvent]s built from ordinary payloads, so they travel the real pipeline:
 * privacy masking, priority resolution, coalescing, expiration, the state machine, the queue and the
 * renderers. Demo mode is therefore a genuine end-to-end test of the product, not a mock-up — with
 * one deliberate difference: [dev.island.service.IslandActionRunner] ignores control actions while
 * demo mode is on, so a simulated track never pauses the user's real music and a simulated call
 * never touches the dialer.
 *
 * Nothing here reads device state; every value is a literal, so demos look identical on every phone.
 */
object DemoEvents {

    private val clock: IslandClock get() = AppGraph.clock
    private val factories get() = AppGraph.eventFactories

    private const val DEMO_PACKAGE = "dev.island.demo"

    // region notifications

    fun message(): IslandEvent.Notification {
        val now = clock.wallClockMs()
        val info = NotificationInfo(
            key = "demo:message:$now",
            packageName = DEMO_PACKAGE,
            appName = "Messages",
            title = "Ava Chen",
            text = "Are we still on for 7? I can grab the table.",
            importance = NotificationImportance.DEFAULT,
            postedAtMs = now,
            personCount = 1,
        )
        return IslandEvent.Notification(
            meta = notificationMeta(info, now),
            notification = info,
        )
    }

    fun groupedMessages(): IslandEvent.Notification {
        val now = clock.wallClockMs()
        val primary = NotificationInfo(
            key = "demo:group:$now",
            packageName = DEMO_PACKAGE,
            appName = "Messages",
            title = "Design review",
            text = "Moving the island 2dp up fixes the cutout overlap.",
            importance = NotificationImportance.DEFAULT,
            postedAtMs = now,
            groupKey = "demo-group",
            groupSize = 3,
        )
        val siblings = listOf(
            primary.copy(
                key = "demo:group:$now:2",
                title = "Priya",
                text = "Agreed — the pill should never touch the camera.",
            ),
            primary.copy(
                key = "demo:group:$now:3",
                title = "Marcus",
                text = "Shipping that today.",
            ),
        )
        return IslandEvent.Notification(
            meta = notificationMeta(primary, now),
            notification = primary,
            grouped = siblings,
        )
    }

    private fun notificationMeta(info: NotificationInfo, now: Long) = IslandEventMeta(
        id = "demo-notification:${info.key}",
        type = IslandEventType.NOTIFICATION,
        priority = IslandPriority.MEDIUM,
        createdAt = now,
        expiresAt = now + TRANSIENT_DEMO_MS,
        title = info.title ?: info.appName,
        subtitle = info.text,
        iconKey = IslandIconKey.NOTIFICATION,
        actions = listOf(dismiss()),
        sourcePackage = info.packageName,
        sourceLabel = info.appName,
        coalesceKey = "demo-notification:${info.key}",
    )

    // endregion

    // region media

    fun music(playing: Boolean = true): IslandEvent.Media {
        val now = clock.elapsedMs()
        return factories.media(
            MediaInfo(
                title = "Midnight City Lights",
                artist = "Neon Harbour",
                album = "Long Way Home",
                durationMs = 224_000L,
                positionMs = if (playing) 76_000L else 76_000L,
                positionSampledAtElapsedMs = now,
                state = if (playing) PlaybackState.PLAYING else PlaybackState.PAUSED,
                artworkToken = null,
                sourcePackage = DEMO_PACKAGE,
                sourceLabel = "Demo Music",
                canPlayPause = true,
                canSkipNext = true,
                canSkipPrevious = true,
                canSeek = true,
            ),
        )
    }

    // endregion

    // region timers

    fun timer(running: Boolean = true): IslandEvent.Timer {
        val now = clock.elapsedMs()
        return factories.timer(
            TimerInfo(
                timerId = "demo-timer",
                label = "Pasta",
                totalMs = 600_000L,
                remainingMs = 372_000L,
                running = running,
                sampledAtElapsedMs = now,
            ),
        )
    }

    fun timerFinished(): IslandEvent.Timer {
        val now = clock.elapsedMs()
        return factories.timer(
            TimerInfo(
                timerId = "demo-timer-done",
                label = "Pasta",
                totalMs = 600_000L,
                remainingMs = 0L,
                running = false,
                finished = true,
                sampledAtElapsedMs = now,
            ),
        )
    }

    fun stopwatch(running: Boolean = true): IslandEvent.Stopwatch {
        val now = clock.elapsedMs()
        return factories.stopwatch(
            StopwatchInfo(
                elapsedMs = 95_400L,
                running = running,
                sampledAtElapsedMs = now,
                laps = listOf(31_200L, 63_800L, 95_400L),
            ),
        )
    }

    // endregion

    // region calls

    fun incomingCall(): IslandEvent.Call? = factories.call(
        CallInfo(
            state = CallState.INCOMING,
            handle = "+1 555 0134",
            canAnswer = true,
            canEnd = true,
            canMute = true,
        ),
    )

    fun activeCall(): IslandEvent.Call? = factories.call(
        CallInfo(
            state = CallState.ACTIVE,
            handle = "+1 555 0134",
            durationMs = 214_000L,
            durationSampledAtElapsedMs = clock.elapsedMs(),
            canEnd = true,
            canMute = true,
        ),
    )

    // endregion

    // region device

    fun charging(): IslandEvent.Charging = factories.chargingStarted(
        ChargingInfo(
            levelPercent = 64,
            plugged = PlugType.USB,
            isCharging = true,
            voltageMv = 9_000,
            currentMicroAmps = 2_500_000,
            temperatureTenthsCelsius = 312,
        ),
    )

    fun batteryLow(): IslandEvent.Battery = factories.battery(
        BatteryInfo(levelPercent = 12, isCharging = false, isLow = true),
    )

    fun batteryFull(): IslandEvent.Battery = factories.battery(
        BatteryInfo(levelPercent = 100, isCharging = false, plugged = PlugType.AC, isFull = true),
    )

    fun headphones(): IslandEvent.Bluetooth = factories.device(
        ConnectedDeviceInfo(
            kind = ConnectedDeviceKind.BLUETOOTH_AUDIO,
            connected = true,
            name = "Studio Buds",
        ),
    )

    fun watch(): IslandEvent.Bluetooth = factories.device(
        ConnectedDeviceInfo(kind = ConnectedDeviceKind.WATCH, connected = true, name = "Watch 4"),
    )

    fun alarm(): IslandEvent.Alarm = factories.alarm(
        AlarmInfo(
            label = "Wake up",
            triggerAtMs = clock.wallClockMs() + 9 * 60_000L,
            isRinging = false,
            canSnooze = true,
        ),
        formattedTime = "07:30",
    )

    // endregion

    // region progress & context

    fun download(): IslandEvent.Download {
        val now = clock.wallClockMs()
        return IslandEvent.Download(
            meta = IslandEventMeta(
                id = "demo-download:$now",
                type = IslandEventType.DOWNLOAD,
                priority = IslandPriority.LOW,
                createdAt = now,
                expiresAt = now + TRANSIENT_DEMO_MS,
                title = "Downloading",
                subtitle = "island-release.apk",
                iconKey = IslandIconKey.DOWNLOAD,
                progress = 0.62f,
                actions = listOf(dismiss()),
                coalesceKey = "demo-download",
            ),
            download = DownloadInfo(
                fileName = "island-release.apk",
                percent = 62,
                downloadedBytes = 24_600_000L,
                totalBytes = 39_700_000L,
                bytesPerSecond = 3_400_000L,
            ),
        )
    }

    fun navigation(): IslandEvent.Navigation {
        val now = clock.wallClockMs()
        return IslandEvent.Navigation(
            meta = IslandEventMeta(
                id = "demo-navigation:$now",
                type = IslandEventType.NAVIGATION,
                priority = IslandPriority.HIGH,
                createdAt = now,
                expiresAt = now + TRANSIENT_DEMO_MS,
                title = "Turn right",
                subtitle = "Harbour Street",
                iconKey = IslandIconKey.NAVIGATION,
                actions = listOf(dismiss()),
                persistent = true,
                coalesceKey = "demo-navigation",
            ),
            navigation = NavigationInfo(
                maneuver = ManeuverKind.RIGHT,
                distanceMeters = 350,
                streetName = "Harbour Street",
                etaMinutes = 12,
                remainingDistanceMeters = 4_800,
                provider = "Demo Maps",
            ),
        )
    }

    fun custom(): IslandEvent.Custom = factories.custom(
        title = "Workout",
        subtitle = "Set 3 of 5 · 12 reps",
        progress = 0.6f,
        iconKey = IslandIconKey.CUSTOM,
        category = "fitness",
        sourcePackage = DEMO_PACKAGE,
        durationMs = TRANSIENT_DEMO_MS,
    )

    // endregion

    /** Every demo event, in the order the demo screen lists them. */
    fun all(): List<Pair<DemoCategoryId, () -> IslandEvent?>> = listOf(
        DemoCategoryId.NOTIFICATION to { message() },
        DemoCategoryId.GROUPED_NOTIFICATION to { groupedMessages() },
        DemoCategoryId.MUSIC to { music() },
        DemoCategoryId.MUSIC_PAUSED to { music(playing = false) },
        DemoCategoryId.TIMER to { timer() },
        DemoCategoryId.TIMER_DONE to { timerFinished() },
        DemoCategoryId.STOPWATCH to { stopwatch() },
        DemoCategoryId.CALL_INCOMING to { incomingCall() },
        DemoCategoryId.CALL_ACTIVE to { activeCall() },
        DemoCategoryId.CHARGING to { charging() },
        DemoCategoryId.BATTERY_LOW to { batteryLow() },
        DemoCategoryId.BATTERY_FULL to { batteryFull() },
        DemoCategoryId.HEADPHONES to { headphones() },
        DemoCategoryId.WATCH to { watch() },
        DemoCategoryId.ALARM to { alarm() },
        DemoCategoryId.DOWNLOAD to { download() },
        DemoCategoryId.NAVIGATION to { navigation() },
        DemoCategoryId.CUSTOM to { custom() },
    )

    /** Builds one demo event; null when a factory declines (e.g. calls on a device without telephony). */
    fun build(category: DemoCategoryId): IslandEvent? =
        all().firstOrNull { it.first == category }?.second?.invoke()

    private fun dismiss() = IslandAction(
        id = "demo-dismiss",
        kind = IslandActionKind.DISMISS,
        iconKey = IslandIconKey.CUSTOM,
        labelKey = dev.island.domain.model.ActionLabelKey.DISMISS,
    )

    private const val TRANSIENT_DEMO_MS = 8_000L
}

/** Stable ids so the demo screen can list events and Compose can key rows. */
enum class DemoCategoryId {
    NOTIFICATION,
    GROUPED_NOTIFICATION,
    MUSIC,
    MUSIC_PAUSED,
    TIMER,
    TIMER_DONE,
    STOPWATCH,
    CALL_INCOMING,
    CALL_ACTIVE,
    CHARGING,
    BATTERY_LOW,
    BATTERY_FULL,
    HEADPHONES,
    WATCH,
    ALARM,
    DOWNLOAD,
    NAVIGATION,
    CUSTOM,
}
