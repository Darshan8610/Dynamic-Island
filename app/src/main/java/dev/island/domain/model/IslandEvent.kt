package dev.island.domain.model

/**
 * Every event source normalises into one of these types. The type drives the renderer,
 * the default priority, the expiration policy and the collapsed/expanded layout.
 */
enum class IslandEventType {
    NOTIFICATION,
    MEDIA,
    TIMER,
    STOPWATCH,
    CALL,
    CHARGING,
    BATTERY,
    BLUETOOTH,
    NAVIGATION,
    DOWNLOAD,
    ALARM,
    SYSTEM,
    CUSTOM,
}

/**
 * Priority bands used by [dev.island.domain.engine.PriorityResolver].
 * Higher [rank] wins focus; equal ranks are ordered by recency.
 */
enum class IslandPriority(val rank: Int) {
    CRITICAL(5),
    HIGH(4),
    MEDIUM(3),
    LOW(2),
    BACKGROUND(1),
    ;

    fun atLeast(other: IslandPriority): Boolean = rank >= other.rank
}

/** How much of an event may be shown outside the app (overlay / lock screen). */
enum class PrivacyLevel {
    /** Title and body may be shown. */
    PUBLIC,

    /** Only a generic summary may be shown ("New message"). */
    PRIVATE,

    /** Never render text; icon only. */
    SECRET,
}

/**
 * Icon vocabulary for the island. Vector icons are resolved in the UI layer
 * ([dev.island.feature.island.ui.widgets]) so the domain stays Android-free.
 */
enum class IslandIconKey {
    APP,
    NOTIFICATION,
    MUSIC,
    TIMER,
    STOPWATCH,
    CALL_INCOMING,
    CALL_ACTIVE,
    CALL_ENDED,
    BATTERY_CHARGING,
    BATTERY_FULL,
    BATTERY_LOW,
    HEADPHONES,
    BLUETOOTH,
    WATCH,
    NAVIGATION,
    DOWNLOAD,
    ALARM,
    INFO,
    WARNING,
    CUSTOM,
}

/** Behaviour bound to an island action button. Handled by [dev.island.domain.usecase.RunIslandAction]. */
enum class IslandActionKind {
    PLAY_PAUSE,
    NEXT,
    PREVIOUS,
    SEEK,
    OPEN_SOURCE_APP,
    OPEN_ISLAND,
    DISMISS,
    ANSWER_CALL,
    END_CALL,
    MUTE_CALL,
    TIMER_PAUSE_RESUME,
    TIMER_STOP,
    TIMER_RESET,
    TIMER_SNOOZE,
    STOPWATCH_LAP,
    STOPWATCH_PAUSE_RESUME,
    STOPWATCH_RESET,
    ALARM_DISMISS,
    ALARM_SNOOZE,
    SOURCE_ACTION,
    CUSTOM,
}

/** Localisable labels for built-in actions. Source-notification actions carry a literal [IslandAction.label]. */
enum class ActionLabelKey {
    PLAY,
    PAUSE,
    NEXT,
    PREVIOUS,
    OPEN,
    DISMISS,
    ANSWER,
    END,
    MUTE,
    UNMUTE,
    PAUSE_TIMER,
    RESUME,
    STOP,
    RESET,
    LAP,
    SNOOZE,
}

/** A single tappable control rendered inside the expanded island. */
data class IslandAction(
    val id: String,
    val kind: IslandActionKind,
    val iconKey: IslandIconKey,
    val labelKey: ActionLabelKey? = null,
    val label: String? = null,
    val enabled: Boolean = true,
    /** Set for [IslandActionKind.SOURCE_ACTION]: the notification action index to fire. */
    val sourceActionIndex: Int? = null,
    /** Set for [IslandActionKind.SEEK]: target position in milliseconds. */
    val seekToMs: Long? = null,
)

/**
 * Fields shared by every island event. Keeping them in one value object means the engine,
 * the queue, the privacy masker and the history store can all work against [IslandEventMeta]
 * without knowing which concrete event they hold.
 */
data class IslandEventMeta(
    val id: String,
    val type: IslandEventType,
    val priority: IslandPriority,
    val createdAt: Long,
    /** Absolute wall-clock deadline; `null` means the event lives until it is removed. */
    val expiresAt: Long? = null,
    val title: String,
    val subtitle: String? = null,
    val iconKey: IslandIconKey = IslandIconKey.INFO,
    /** ARGB accent as a raw value so the domain layer needs no android.graphics dependency. */
    val accentArgb: Long? = null,
    /** 0f..1f, or `null` when the event has no progress. */
    val progress: Float? = null,
    val actions: List<IslandAction> = emptyList(),
    val expandable: Boolean = true,
    /** Persistent events survive their timeout as a compact mini island (music, timer, call…). */
    val persistent: Boolean = false,
    val sourcePackage: String? = null,
    val sourceLabel: String? = null,
    val privacyLevel: PrivacyLevel = PrivacyLevel.PUBLIC,
    /**
     * Events with the same [coalesceKey] replace each other instead of stacking, which is what
     * stops a chatty app from animating the island four times in 100 ms.
     */
    val coalesceKey: String = id,
)

/**
 * The unified island event. One sealed hierarchy, one renderer per event family,
 * one queue for everything.
 */
sealed interface IslandEvent {
    val meta: IslandEventMeta

    val id: String get() = meta.id
    val type: IslandEventType get() = meta.type
    val priority: IslandPriority get() = meta.priority
    val createdAt: Long get() = meta.createdAt
    val expiresAt: Long? get() = meta.expiresAt
    val title: String get() = meta.title
    val subtitle: String? get() = meta.subtitle
    val iconKey: IslandIconKey get() = meta.iconKey
    val accentArgb: Long? get() = meta.accentArgb
    val progress: Float? get() = meta.progress
    val actions: List<IslandAction> get() = meta.actions
    val expandable: Boolean get() = meta.expandable
    val persistent: Boolean get() = meta.persistent
    val sourcePackage: String? get() = meta.sourcePackage
    val sourceLabel: String? get() = meta.sourceLabel
    val privacyLevel: PrivacyLevel get() = meta.privacyLevel
    val coalesceKey: String get() = meta.coalesceKey

    /** Events that keep the island alive as a compact mini island until explicitly stopped. */
    val isPersistentKind: Boolean get() = persistent

    data class Notification(
        override val meta: IslandEventMeta,
        val notification: NotificationInfo,
        /** Sibling notifications from the same app, already grouped by [dev.island.data.notifications.NotificationGrouping]. */
        val grouped: List<NotificationInfo> = emptyList(),
    ) : IslandEvent

    data class Media(
        override val meta: IslandEventMeta,
        val media: MediaInfo,
    ) : IslandEvent

    data class Timer(
        override val meta: IslandEventMeta,
        val timer: TimerInfo,
    ) : IslandEvent

    data class Stopwatch(
        override val meta: IslandEventMeta,
        val stopwatch: StopwatchInfo,
    ) : IslandEvent

    data class Call(
        override val meta: IslandEventMeta,
        val call: CallInfo,
    ) : IslandEvent

    data class Charging(
        override val meta: IslandEventMeta,
        val charging: ChargingInfo,
    ) : IslandEvent

    data class Battery(
        override val meta: IslandEventMeta,
        val battery: BatteryInfo,
    ) : IslandEvent

    data class Bluetooth(
        override val meta: IslandEventMeta,
        val device: ConnectedDeviceInfo,
    ) : IslandEvent

    data class Navigation(
        override val meta: IslandEventMeta,
        val navigation: NavigationInfo,
    ) : IslandEvent

    data class Download(
        override val meta: IslandEventMeta,
        val download: DownloadInfo,
    ) : IslandEvent

    data class Alarm(
        override val meta: IslandEventMeta,
        val alarm: AlarmInfo,
    ) : IslandEvent

    data class System(
        override val meta: IslandEventMeta,
        val system: SystemInfo,
    ) : IslandEvent

    /**
     * Public extension point. Any in-process caller (and, later, a companion app through a
     * documented local interface) can surface a first-class island event with
     * `IslandEvent.Custom(...)`.
     */
    data class Custom(
        override val meta: IslandEventMeta,
        val custom: CustomInfo = CustomInfo(),
    ) : IslandEvent
}
