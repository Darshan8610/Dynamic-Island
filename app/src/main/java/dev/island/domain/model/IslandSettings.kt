package dev.island.domain.model

/** Where the island sits relative to the camera cutout / status bar. */
enum class IslandPosition {
    /** Derive from the live display cutout + status bar insets (recommended). */
    AUTO_CUTOUT,
    TOP_CENTER,
    SLIGHTLY_LOWER,
    CUSTOM,
}

enum class AnimationStyle {
    /** Playful overshoot. */
    SPRINGY,

    /** Balanced spring, the default. */
    SMOOTH,

    /** Short, snappy motion for users who want minimal movement. */
    SNAPPY,
}

enum class IslandThemeMode {
    /** #000000 pill for OLED displays. */
    PURE_BLACK,

    /** Near-black grey, softer in bright rooms. */
    DARK_GRAY,

    /** Follow Android dynamic colour where the platform supports it. */
    DYNAMIC,
}

/** How much of a notification the island may reveal. */
enum class PrivacyMode {
    /** Title + content. */
    FULL,

    /** "New message" style generic text. */
    PRIVATE,

    /** App icon only. */
    ICON_ONLY,

    /** Full preview, but never for notifications the source marked sensitive/private. */
    SENSITIVE_HIDDEN,
}

/** Ready-made looks. [apply] returns the adjusted appearance; [CUSTOM] keeps user values. */
enum class IslandPreset {
    MINIMAL,
    CLASSIC,
    COMPACT,
    LARGE,
    GAMING,
    MUSIC,
    TRANSPARENT,
    DYNAMIC,
    CUSTOM,
    ;

    fun apply(base: IslandAppearance): IslandAppearance = when (this) {
        MINIMAL -> base.copy(
            sizeScale = 0.9f,
            cornerRadiusDp = 18f,
            opacity = 1f,
            blurEnabled = false,
            animationStyle = AnimationStyle.SNAPPY,
            iconScale = 0.9f,
            textScale = 0.95f,
            themeMode = IslandThemeMode.PURE_BLACK,
        )

        CLASSIC -> base.copy(
            sizeScale = 1f,
            cornerRadiusDp = 22f,
            opacity = 1f,
            blurEnabled = true,
            blurRadius = 18f,
            animationStyle = AnimationStyle.SMOOTH,
            iconScale = 1f,
            textScale = 1f,
            themeMode = IslandThemeMode.PURE_BLACK,
        )

        COMPACT -> base.copy(
            sizeScale = 0.82f,
            cornerRadiusDp = 16f,
            iconScale = 0.85f,
            textScale = 0.9f,
            animationStyle = AnimationStyle.SNAPPY,
        )

        LARGE -> base.copy(
            sizeScale = 1.18f,
            cornerRadiusDp = 26f,
            iconScale = 1.1f,
            textScale = 1.08f,
            animationStyle = AnimationStyle.SMOOTH,
        )

        GAMING -> base.copy(
            sizeScale = 0.95f,
            cornerRadiusDp = 12f,
            opacity = 0.92f,
            blurEnabled = true,
            blurRadius = 26f,
            animationStyle = AnimationStyle.SNAPPY,
            themeMode = IslandThemeMode.DARK_GRAY,
        )

        MUSIC -> base.copy(
            sizeScale = 1.05f,
            cornerRadiusDp = 24f,
            blurEnabled = true,
            blurRadius = 22f,
            animationStyle = AnimationStyle.SPRINGY,
        )

        TRANSPARENT -> base.copy(
            opacity = 0.72f,
            blurEnabled = true,
            blurRadius = 30f,
            cornerRadiusDp = 22f,
            themeMode = IslandThemeMode.DYNAMIC,
        )

        DYNAMIC -> base.copy(
            themeMode = IslandThemeMode.DYNAMIC,
            dynamicColor = true,
            cornerRadiusDp = 22f,
            animationStyle = AnimationStyle.SMOOTH,
        )

        CUSTOM -> base
    }
}

enum class LandscapeBehavior {
    /** Move to the safe top-centre position and use the compact landscape layout. */
    ADAPT,

    /** Shrink to a dot-sized indicator. */
    MINIMIZE,

    /** Hide the island while in landscape. */
    HIDE,
}

enum class TapAction { EXPAND, EXPAND_OR_OPEN_SOURCE, OPEN_SOURCE_APP }
enum class DoubleTapAction { TOGGLE_EXPAND, DISMISS, CYCLE_EVENT }
enum class LongPressAction { OPEN_ISLAND_SETTINGS, OPEN_SOURCE_APP, DISMISS_EVENT, PIN_EVENT }
enum class HorizontalSwipeAction { CYCLE_EVENT, DISMISS_EVENT, NONE }
enum class VerticalSwipeAction { EXPAND, DISMISS_EVENT, NONE }

/** Everything the island renderer needs to lay itself out and animate. */
data class IslandAppearance(
    val preset: IslandPreset = IslandPreset.CLASSIC,
    val sizeScale: Float = 1f,
    val cornerRadiusDp: Float = 22f,
    val horizontalOffsetDp: Float = 0f,
    val verticalOffsetDp: Float = 0f,
    val animationSpeed: Float = 1f,
    val animationStyle: AnimationStyle = AnimationStyle.SMOOTH,
    val iconScale: Float = 1f,
    val textScale: Float = 1f,
    val opacity: Float = 1f,
    val blurEnabled: Boolean = true,
    val blurRadius: Float = 18f,
    val themeMode: IslandThemeMode = IslandThemeMode.PURE_BLACK,
    val dynamicColor: Boolean = false,
    val accentArgb: Long? = null,
) {
    companion object {
        const val MIN_SIZE_SCALE = 0.7f
        const val MAX_SIZE_SCALE = 1.5f
        const val MIN_ANIMATION_SPEED = 0.5f
        const val MAX_ANIMATION_SPEED = 1.5f
    }
}

data class NotificationSettings(
    val enabled: Boolean = true,
    val privacyMode: PrivacyMode = PrivacyMode.FULL,
    val hideSensitiveContent: Boolean = true,
    val groupNotifications: Boolean = true,
    val minImportance: NotificationImportance = NotificationImportance.LOW,
    val showOngoingNotifications: Boolean = false,
    val expandOnHighImportance: Boolean = true,
    val appRules: Map<String, AppNotificationRule> = emptyMap(),
    /**
     * Apps that have posted a notification while Island was running: package name → label.
     * Island never enumerates installed apps in advance (no QUERY_ALL_PACKAGES).
     */
    val seenApps: Map<String, String> = emptyMap(),
) {
    fun modeFor(packageName: String): AppNotificationMode =
        appRules[packageName]?.mode ?: AppNotificationMode.ALWAYS
}

data class MediaSettings(
    val enabled: Boolean = true,
    val showAlbumArt: Boolean = true,
    val showTransportControls: Boolean = true,
    val showProgressBar: Boolean = true,
    val showCollapsedWaveform: Boolean = true,
    val allowSeek: Boolean = true,
    val collapseAfterIntroMs: Long = 2_600L,
)

data class CallSettings(
    val showIncoming: Boolean = true,
    val showOngoing: Boolean = true,
    val showControls: Boolean = true,
    val showHandle: Boolean = true,
)

data class BatterySettings(
    val showCharging: Boolean = true,
    val showFullCharge: Boolean = true,
    val showLowBattery: Boolean = true,
    val chargingAnimation: Boolean = true,
    val showWattage: Boolean = true,
    val thresholdEnabled: Boolean = false,
    val thresholdPercent: Int = 80,
    val lowBatteryPercent: Int = 15,
)

data class DeviceSettings(
    val bluetoothEvents: Boolean = true,
    val wiredHeadsetEvents: Boolean = true,
    val watchEvents: Boolean = true,
    val carEvents: Boolean = false,
    val allowedKinds: Set<ConnectedDeviceKind> = setOf(
        ConnectedDeviceKind.WIRED_HEADSET,
        ConnectedDeviceKind.BLUETOOTH_AUDIO,
        ConnectedDeviceKind.WATCH,
    ),
    val alarmEvents: Boolean = true,
    val alarmLeadTimeMinutes: Int = 15,
)

data class BehaviorSettings(
    val tapAction: TapAction = TapAction.EXPAND,
    val doubleTapAction: DoubleTapAction = DoubleTapAction.TOGGLE_EXPAND,
    val longPressAction: LongPressAction = LongPressAction.OPEN_ISLAND_SETTINGS,
    val horizontalSwipeAction: HorizontalSwipeAction = HorizontalSwipeAction.CYCLE_EVENT,
    val swipeUpAction: VerticalSwipeAction = VerticalSwipeAction.DISMISS_EVENT,
    val swipeDownAction: VerticalSwipeAction = VerticalSwipeAction.EXPAND,
    val autoCollapseSeconds: Int = 6,
    val followSystemDnd: Boolean = true,
    val allowCriticalDuringDnd: Boolean = true,
    val landscapeBehavior: LandscapeBehavior = LandscapeBehavior.ADAPT,
    val hideWhenScreenOff: Boolean = true,
    val showOnLockScreen: Boolean = false,
    val hapticFeedback: Boolean = true,
    val reduceMotion: Boolean = false,
    val position: IslandPosition = IslandPosition.AUTO_CUTOUT,
    val customVerticalOffsetDp: Float = 0f,
    val keepPersistentMiniIsland: Boolean = true,
)

data class PrivacySettings(
    val hideSensitiveText: Boolean = true,
    val lockScreenMode: PrivacyMode = PrivacyMode.ICON_ONLY,
    val historyEnabled: Boolean = false,
    val historyStoresContent: Boolean = false,
    val historyRetentionDays: Int = 7,
)

data class AdvancedSettings(
    val debugMode: Boolean = false,
    val eventLoggerEnabled: Boolean = true,
    val demoMode: Boolean = false,
    val performanceMonitor: Boolean = false,
)

/**
 * The complete, immutable configuration snapshot. [dev.island.data.preferences.DataStoreSettingsRepository]
 * is the single writer; everything else observes a `Flow<IslandSettings>`.
 */
data class IslandSettings(
    val islandEnabled: Boolean = false,
    val onboardingComplete: Boolean = false,
    val appearance: IslandAppearance = IslandAppearance(),
    val notifications: NotificationSettings = NotificationSettings(),
    val media: MediaSettings = MediaSettings(),
    val calls: CallSettings = CallSettings(),
    val battery: BatterySettings = BatterySettings(),
    val devices: DeviceSettings = DeviceSettings(),
    val behavior: BehaviorSettings = BehaviorSettings(),
    val privacy: PrivacySettings = PrivacySettings(),
    val advanced: AdvancedSettings = AdvancedSettings(),
    val lastKnownServiceActive: Boolean = false,
) {
    companion object {
        val Default = IslandSettings()
    }
}
