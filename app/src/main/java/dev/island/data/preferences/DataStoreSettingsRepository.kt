package dev.island.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.island.core.logging.IslandLogger
import dev.island.domain.model.AdvancedSettings
import dev.island.domain.model.AnimationStyle
import dev.island.domain.model.AppNotificationMode
import dev.island.domain.model.AppNotificationRule
import dev.island.domain.model.BatterySettings
import dev.island.domain.model.BehaviorSettings
import dev.island.domain.model.CallSettings
import dev.island.domain.model.ConnectedDeviceKind
import dev.island.domain.model.DeviceSettings
import dev.island.domain.model.DoubleTapAction
import dev.island.domain.model.HorizontalSwipeAction
import dev.island.domain.model.IslandAppearance
import dev.island.domain.model.IslandPosition
import dev.island.domain.model.IslandPreset
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.IslandThemeMode
import dev.island.domain.model.LandscapeBehavior
import dev.island.domain.model.LongPressAction
import dev.island.domain.model.MediaSettings
import dev.island.domain.model.NotificationImportance
import dev.island.domain.model.NotificationSettings
import dev.island.domain.model.PrivacyMode
import dev.island.domain.model.PrivacySettings
import dev.island.domain.model.TapAction
import dev.island.domain.model.VerticalSwipeAction
import dev.island.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.islandDataStore: DataStore<Preferences> by preferencesDataStore(name = "island_settings")

/** Receiver of the DataStore edit block; a file-level alias because typealiases cannot be nested. */
private typealias PrefsWriter = androidx.datastore.preferences.core.MutablePreferences

/** Every preference key in one place — no string literals scattered through the app. */
internal object Keys {
    val ISLAND_ENABLED = booleanPreferencesKey("island_enabled")
    val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    val LAST_SERVICE_ACTIVE = booleanPreferencesKey("last_service_active")

    val PRESET = stringPreferencesKey("appearance_preset")
    val SIZE_SCALE = floatPreferencesKey("appearance_size_scale")
    val CORNER_RADIUS_DP = floatPreferencesKey("appearance_corner_radius_dp")
    val H_OFFSET_DP = floatPreferencesKey("appearance_h_offset_dp")
    val V_OFFSET_DP = floatPreferencesKey("appearance_v_offset_dp")
    val ANIMATION_SPEED = floatPreferencesKey("appearance_animation_speed")
    val ANIMATION_STYLE = stringPreferencesKey("appearance_animation_style")
    val ICON_SCALE = floatPreferencesKey("appearance_icon_scale")
    val TEXT_SCALE = floatPreferencesKey("appearance_text_scale")
    val OPACITY = floatPreferencesKey("appearance_opacity")
    val BLUR_ENABLED = booleanPreferencesKey("appearance_blur_enabled")
    val BLUR_RADIUS = floatPreferencesKey("appearance_blur_radius")
    val THEME_MODE = stringPreferencesKey("appearance_theme_mode")
    val DYNAMIC_COLOR = booleanPreferencesKey("appearance_dynamic_color")
    val ACCENT_ARGB = longPreferencesKey("appearance_accent_argb")

    val NOTIF_ENABLED = booleanPreferencesKey("notifications_enabled")
    val PRIVACY_MODE = stringPreferencesKey("notifications_privacy_mode")
    val HIDE_SENSITIVE = booleanPreferencesKey("notifications_hide_sensitive")
    val GROUP_NOTIFICATIONS = booleanPreferencesKey("notifications_group")
    val MIN_IMPORTANCE = intPreferencesKey("notifications_min_importance")
    val SHOW_ONGOING = booleanPreferencesKey("notifications_show_ongoing")
    val EXPAND_ON_HIGH = booleanPreferencesKey("notifications_expand_on_high")
    val APP_RULES = stringPreferencesKey("notifications_app_rules")
    val SEEN_APPS = stringPreferencesKey("notifications_seen_apps")

    val MEDIA_ENABLED = booleanPreferencesKey("media_enabled")
    val MEDIA_ALBUM_ART = booleanPreferencesKey("media_album_art")
    val MEDIA_CONTROLS = booleanPreferencesKey("media_controls")
    val MEDIA_PROGRESS = booleanPreferencesKey("media_progress")
    val MEDIA_WAVEFORM = booleanPreferencesKey("media_waveform")
    val MEDIA_SEEK = booleanPreferencesKey("media_seek")
    val MEDIA_INTRO_MS = longPreferencesKey("media_intro_ms")

    val CALL_INCOMING = booleanPreferencesKey("calls_incoming")
    val CALL_ONGOING = booleanPreferencesKey("calls_ongoing")
    val CALL_CONTROLS = booleanPreferencesKey("calls_controls")
    val CALL_HANDLE = booleanPreferencesKey("calls_handle")

    val BATTERY_CHARGING = booleanPreferencesKey("battery_charging")
    val BATTERY_FULL = booleanPreferencesKey("battery_full")
    val BATTERY_LOW = booleanPreferencesKey("battery_low")
    val BATTERY_ANIMATION = booleanPreferencesKey("battery_animation")
    val BATTERY_WATTAGE = booleanPreferencesKey("battery_wattage")
    val BATTERY_THRESHOLD_ENABLED = booleanPreferencesKey("battery_threshold_enabled")
    val BATTERY_THRESHOLD_PERCENT = intPreferencesKey("battery_threshold_percent")
    val BATTERY_LOW_PERCENT = intPreferencesKey("battery_low_percent")

    val DEVICE_BLUETOOTH = booleanPreferencesKey("devices_bluetooth")
    val DEVICE_WIRED = booleanPreferencesKey("devices_wired")
    val DEVICE_WATCH = booleanPreferencesKey("devices_watch")
    val DEVICE_CAR = booleanPreferencesKey("devices_car")
    val DEVICE_ALLOWED_KINDS = stringPreferencesKey("devices_allowed_kinds")
    val DEVICE_ALARMS = booleanPreferencesKey("devices_alarms")
    val DEVICE_ALARM_LEAD_MIN = intPreferencesKey("devices_alarm_lead_minutes")

    val BEHAVIOR_TAP = stringPreferencesKey("behavior_tap")
    val BEHAVIOR_DOUBLE_TAP = stringPreferencesKey("behavior_double_tap")
    val BEHAVIOR_LONG_PRESS = stringPreferencesKey("behavior_long_press")
    val BEHAVIOR_SWIPE_H = stringPreferencesKey("behavior_swipe_h")
    val BEHAVIOR_SWIPE_UP = stringPreferencesKey("behavior_swipe_up")
    val BEHAVIOR_SWIPE_DOWN = stringPreferencesKey("behavior_swipe_down")
    val BEHAVIOR_AUTO_COLLAPSE_S = intPreferencesKey("behavior_auto_collapse_seconds")
    val BEHAVIOR_FOLLOW_DND = booleanPreferencesKey("behavior_follow_dnd")
    val BEHAVIOR_CRITICAL_DND = booleanPreferencesKey("behavior_critical_during_dnd")
    val BEHAVIOR_LANDSCAPE = stringPreferencesKey("behavior_landscape")
    val BEHAVIOR_HIDE_SCREEN_OFF = booleanPreferencesKey("behavior_hide_screen_off")
    val BEHAVIOR_SHOW_LOCK_SCREEN = booleanPreferencesKey("behavior_show_lock_screen")
    val BEHAVIOR_HAPTICS = booleanPreferencesKey("behavior_haptics")
    val BEHAVIOR_REDUCE_MOTION = booleanPreferencesKey("behavior_reduce_motion")
    val BEHAVIOR_POSITION = stringPreferencesKey("behavior_position")
    val BEHAVIOR_CUSTOM_V_OFFSET = floatPreferencesKey("behavior_custom_v_offset")
    val BEHAVIOR_KEEP_MINI = booleanPreferencesKey("behavior_keep_mini_island")

    val PRIVACY_HIDE_SENSITIVE_TEXT = booleanPreferencesKey("privacy_hide_sensitive_text")
    val PRIVACY_LOCK_SCREEN_MODE = stringPreferencesKey("privacy_lock_screen_mode")
    val PRIVACY_HISTORY_ENABLED = booleanPreferencesKey("privacy_history_enabled")
    val PRIVACY_HISTORY_CONTENT = booleanPreferencesKey("privacy_history_content")
    val PRIVACY_HISTORY_RETENTION_DAYS = intPreferencesKey("privacy_history_retention_days")

    val ADVANCED_DEBUG = booleanPreferencesKey("advanced_debug")
    val ADVANCED_EVENT_LOGGER = booleanPreferencesKey("advanced_event_logger")
    val ADVANCED_DEMO = booleanPreferencesKey("advanced_demo")
    val ADVANCED_PERF_MONITOR = booleanPreferencesKey("advanced_performance_monitor")
}

/**
 * DataStore-backed settings. The single writer for user configuration.
 *
 * Corrupted or partially written preferences fall back to defaults instead of crashing
 * (see [Flow.catch]); unknown enum names fall back to their default value.
 */
class DataStoreSettingsRepository(
    private val context: Context,
    scope: CoroutineScope,
    private val logger: IslandLogger,
) : SettingsRepository {

    private val defaults = IslandSettings.Default

    override val settings: Flow<IslandSettings> = context.islandDataStore.data
        .catch { cause ->
            // DataStore throws IOException when the file is unreadable: recover with defaults.
            if (cause is IOException) {
                logger.w(TAG, "settings unreadable, falling back to defaults", cause)
                emit(emptyPreferences())
            } else {
                throw cause
            }
        }
        .map { prefs -> prefs.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, defaults)

    override suspend fun current(): IslandSettings = settings.first()

    override suspend fun update(transform: (IslandSettings) -> IslandSettings) {
        context.islandDataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs.write(next)
        }
    }

    override suspend fun reset(): IslandSettings {
        context.islandDataStore.edit { prefs ->
            prefs.clear()
            prefs.write(defaults)
        }
        logger.i(TAG, "settings reset to defaults")
        return defaults
    }

    // region mapping

    private fun Preferences.toSettings(): IslandSettings = IslandSettings(
        islandEnabled = this[Keys.ISLAND_ENABLED] ?: defaults.islandEnabled,
        onboardingComplete = this[Keys.ONBOARDING_COMPLETE] ?: defaults.onboardingComplete,
        lastKnownServiceActive = this[Keys.LAST_SERVICE_ACTIVE] ?: defaults.lastKnownServiceActive,
        appearance = IslandAppearance(
            preset = enum(this[Keys.PRESET], defaults.appearance.preset),
            sizeScale = (this[Keys.SIZE_SCALE] ?: defaults.appearance.sizeScale)
                .coerceIn(IslandAppearance.MIN_SIZE_SCALE, IslandAppearance.MAX_SIZE_SCALE),
            cornerRadiusDp = (this[Keys.CORNER_RADIUS_DP] ?: defaults.appearance.cornerRadiusDp).coerceIn(4f, 40f),
            horizontalOffsetDp = this[Keys.H_OFFSET_DP] ?: defaults.appearance.horizontalOffsetDp,
            verticalOffsetDp = this[Keys.V_OFFSET_DP] ?: defaults.appearance.verticalOffsetDp,
            animationSpeed = (this[Keys.ANIMATION_SPEED] ?: defaults.appearance.animationSpeed)
                .coerceIn(IslandAppearance.MIN_ANIMATION_SPEED, IslandAppearance.MAX_ANIMATION_SPEED),
            animationStyle = enum(this[Keys.ANIMATION_STYLE], defaults.appearance.animationStyle),
            iconScale = (this[Keys.ICON_SCALE] ?: defaults.appearance.iconScale).coerceIn(0.6f, 1.6f),
            textScale = (this[Keys.TEXT_SCALE] ?: defaults.appearance.textScale).coerceIn(0.7f, 1.5f),
            opacity = (this[Keys.OPACITY] ?: defaults.appearance.opacity).coerceIn(0.4f, 1f),
            blurEnabled = this[Keys.BLUR_ENABLED] ?: defaults.appearance.blurEnabled,
            blurRadius = (this[Keys.BLUR_RADIUS] ?: defaults.appearance.blurRadius).coerceIn(0f, 40f),
            themeMode = enum(this[Keys.THEME_MODE], defaults.appearance.themeMode),
            dynamicColor = this[Keys.DYNAMIC_COLOR] ?: defaults.appearance.dynamicColor,
            accentArgb = this[Keys.ACCENT_ARGB]?.takeIf { it != ACCENT_UNSET },
        ),
        notifications = NotificationSettings(
            enabled = this[Keys.NOTIF_ENABLED] ?: defaults.notifications.enabled,
            privacyMode = enum(this[Keys.PRIVACY_MODE], defaults.notifications.privacyMode),
            hideSensitiveContent = this[Keys.HIDE_SENSITIVE] ?: defaults.notifications.hideSensitiveContent,
            groupNotifications = this[Keys.GROUP_NOTIFICATIONS] ?: defaults.notifications.groupNotifications,
            minImportance = NotificationImportance.from(this[Keys.MIN_IMPORTANCE] ?: defaults.notifications.minImportance.value),
            showOngoingNotifications = this[Keys.SHOW_ONGOING] ?: defaults.notifications.showOngoingNotifications,
            expandOnHighImportance = this[Keys.EXPAND_ON_HIGH] ?: defaults.notifications.expandOnHighImportance,
            appRules = decodeAppRules(this[Keys.APP_RULES]),
            seenApps = decodeSeenApps(this[Keys.SEEN_APPS]),
        ),
        media = MediaSettings(
            enabled = this[Keys.MEDIA_ENABLED] ?: defaults.media.enabled,
            showAlbumArt = this[Keys.MEDIA_ALBUM_ART] ?: defaults.media.showAlbumArt,
            showTransportControls = this[Keys.MEDIA_CONTROLS] ?: defaults.media.showTransportControls,
            showProgressBar = this[Keys.MEDIA_PROGRESS] ?: defaults.media.showProgressBar,
            showCollapsedWaveform = this[Keys.MEDIA_WAVEFORM] ?: defaults.media.showCollapsedWaveform,
            allowSeek = this[Keys.MEDIA_SEEK] ?: defaults.media.allowSeek,
            collapseAfterIntroMs = this[Keys.MEDIA_INTRO_MS] ?: defaults.media.collapseAfterIntroMs,
        ),
        calls = CallSettings(
            showIncoming = this[Keys.CALL_INCOMING] ?: defaults.calls.showIncoming,
            showOngoing = this[Keys.CALL_ONGOING] ?: defaults.calls.showOngoing,
            showControls = this[Keys.CALL_CONTROLS] ?: defaults.calls.showControls,
            showHandle = this[Keys.CALL_HANDLE] ?: defaults.calls.showHandle,
        ),
        battery = BatterySettings(
            showCharging = this[Keys.BATTERY_CHARGING] ?: defaults.battery.showCharging,
            showFullCharge = this[Keys.BATTERY_FULL] ?: defaults.battery.showFullCharge,
            showLowBattery = this[Keys.BATTERY_LOW] ?: defaults.battery.showLowBattery,
            chargingAnimation = this[Keys.BATTERY_ANIMATION] ?: defaults.battery.chargingAnimation,
            showWattage = this[Keys.BATTERY_WATTAGE] ?: defaults.battery.showWattage,
            thresholdEnabled = this[Keys.BATTERY_THRESHOLD_ENABLED] ?: defaults.battery.thresholdEnabled,
            thresholdPercent = (this[Keys.BATTERY_THRESHOLD_PERCENT] ?: defaults.battery.thresholdPercent).coerceIn(10, 100),
            lowBatteryPercent = (this[Keys.BATTERY_LOW_PERCENT] ?: defaults.battery.lowBatteryPercent).coerceIn(5, 40),
        ),
        devices = DeviceSettings(
            bluetoothEvents = this[Keys.DEVICE_BLUETOOTH] ?: defaults.devices.bluetoothEvents,
            wiredHeadsetEvents = this[Keys.DEVICE_WIRED] ?: defaults.devices.wiredHeadsetEvents,
            watchEvents = this[Keys.DEVICE_WATCH] ?: defaults.devices.watchEvents,
            carEvents = this[Keys.DEVICE_CAR] ?: defaults.devices.carEvents,
            allowedKinds = decodeKinds(this[Keys.DEVICE_ALLOWED_KINDS]) ?: defaults.devices.allowedKinds,
            alarmEvents = this[Keys.DEVICE_ALARMS] ?: defaults.devices.alarmEvents,
            alarmLeadTimeMinutes = (this[Keys.DEVICE_ALARM_LEAD_MIN] ?: defaults.devices.alarmLeadTimeMinutes).coerceIn(1, 120),
        ),
        behavior = BehaviorSettings(
            tapAction = enum(this[Keys.BEHAVIOR_TAP], defaults.behavior.tapAction),
            doubleTapAction = enum(this[Keys.BEHAVIOR_DOUBLE_TAP], defaults.behavior.doubleTapAction),
            longPressAction = enum(this[Keys.BEHAVIOR_LONG_PRESS], defaults.behavior.longPressAction),
            horizontalSwipeAction = enum(this[Keys.BEHAVIOR_SWIPE_H], defaults.behavior.horizontalSwipeAction),
            swipeUpAction = enum(this[Keys.BEHAVIOR_SWIPE_UP], defaults.behavior.swipeUpAction),
            swipeDownAction = enum(this[Keys.BEHAVIOR_SWIPE_DOWN], defaults.behavior.swipeDownAction),
            autoCollapseSeconds = (this[Keys.BEHAVIOR_AUTO_COLLAPSE_S] ?: defaults.behavior.autoCollapseSeconds).coerceIn(2, 30),
            followSystemDnd = this[Keys.BEHAVIOR_FOLLOW_DND] ?: defaults.behavior.followSystemDnd,
            allowCriticalDuringDnd = this[Keys.BEHAVIOR_CRITICAL_DND] ?: defaults.behavior.allowCriticalDuringDnd,
            landscapeBehavior = enum(this[Keys.BEHAVIOR_LANDSCAPE], defaults.behavior.landscapeBehavior),
            hideWhenScreenOff = this[Keys.BEHAVIOR_HIDE_SCREEN_OFF] ?: defaults.behavior.hideWhenScreenOff,
            showOnLockScreen = this[Keys.BEHAVIOR_SHOW_LOCK_SCREEN] ?: defaults.behavior.showOnLockScreen,
            hapticFeedback = this[Keys.BEHAVIOR_HAPTICS] ?: defaults.behavior.hapticFeedback,
            reduceMotion = this[Keys.BEHAVIOR_REDUCE_MOTION] ?: defaults.behavior.reduceMotion,
            position = enum(this[Keys.BEHAVIOR_POSITION], defaults.behavior.position),
            customVerticalOffsetDp = this[Keys.BEHAVIOR_CUSTOM_V_OFFSET] ?: defaults.behavior.customVerticalOffsetDp,
            keepPersistentMiniIsland = this[Keys.BEHAVIOR_KEEP_MINI] ?: defaults.behavior.keepPersistentMiniIsland,
        ),
        privacy = PrivacySettings(
            hideSensitiveText = this[Keys.PRIVACY_HIDE_SENSITIVE_TEXT] ?: defaults.privacy.hideSensitiveText,
            lockScreenMode = enum(this[Keys.PRIVACY_LOCK_SCREEN_MODE], defaults.privacy.lockScreenMode),
            historyEnabled = this[Keys.PRIVACY_HISTORY_ENABLED] ?: defaults.privacy.historyEnabled,
            historyStoresContent = this[Keys.PRIVACY_HISTORY_CONTENT] ?: defaults.privacy.historyStoresContent,
            historyRetentionDays = (this[Keys.PRIVACY_HISTORY_RETENTION_DAYS] ?: defaults.privacy.historyRetentionDays).coerceIn(1, 90),
        ),
        advanced = AdvancedSettings(
            debugMode = this[Keys.ADVANCED_DEBUG] ?: defaults.advanced.debugMode,
            eventLoggerEnabled = this[Keys.ADVANCED_EVENT_LOGGER] ?: defaults.advanced.eventLoggerEnabled,
            demoMode = this[Keys.ADVANCED_DEMO] ?: defaults.advanced.demoMode,
            performanceMonitor = this[Keys.ADVANCED_PERF_MONITOR] ?: defaults.advanced.performanceMonitor,
        ),
    )

    private fun PrefsWriter.write(s: IslandSettings) {
        this[Keys.ISLAND_ENABLED] = s.islandEnabled
        this[Keys.ONBOARDING_COMPLETE] = s.onboardingComplete
        this[Keys.LAST_SERVICE_ACTIVE] = s.lastKnownServiceActive

        this[Keys.PRESET] = s.appearance.preset.name
        this[Keys.SIZE_SCALE] = s.appearance.sizeScale
        this[Keys.CORNER_RADIUS_DP] = s.appearance.cornerRadiusDp
        this[Keys.H_OFFSET_DP] = s.appearance.horizontalOffsetDp
        this[Keys.V_OFFSET_DP] = s.appearance.verticalOffsetDp
        this[Keys.ANIMATION_SPEED] = s.appearance.animationSpeed
        this[Keys.ANIMATION_STYLE] = s.appearance.animationStyle.name
        this[Keys.ICON_SCALE] = s.appearance.iconScale
        this[Keys.TEXT_SCALE] = s.appearance.textScale
        this[Keys.OPACITY] = s.appearance.opacity
        this[Keys.BLUR_ENABLED] = s.appearance.blurEnabled
        this[Keys.BLUR_RADIUS] = s.appearance.blurRadius
        this[Keys.THEME_MODE] = s.appearance.themeMode.name
        this[Keys.DYNAMIC_COLOR] = s.appearance.dynamicColor
        this[Keys.ACCENT_ARGB] = s.appearance.accentArgb ?: ACCENT_UNSET

        this[Keys.NOTIF_ENABLED] = s.notifications.enabled
        this[Keys.PRIVACY_MODE] = s.notifications.privacyMode.name
        this[Keys.HIDE_SENSITIVE] = s.notifications.hideSensitiveContent
        this[Keys.GROUP_NOTIFICATIONS] = s.notifications.groupNotifications
        this[Keys.MIN_IMPORTANCE] = s.notifications.minImportance.value
        this[Keys.SHOW_ONGOING] = s.notifications.showOngoingNotifications
        this[Keys.EXPAND_ON_HIGH] = s.notifications.expandOnHighImportance
        this[Keys.APP_RULES] = encodeAppRules(s.notifications.appRules)
        this[Keys.SEEN_APPS] = encodeSeenApps(s.notifications.seenApps)

        this[Keys.MEDIA_ENABLED] = s.media.enabled
        this[Keys.MEDIA_ALBUM_ART] = s.media.showAlbumArt
        this[Keys.MEDIA_CONTROLS] = s.media.showTransportControls
        this[Keys.MEDIA_PROGRESS] = s.media.showProgressBar
        this[Keys.MEDIA_WAVEFORM] = s.media.showCollapsedWaveform
        this[Keys.MEDIA_SEEK] = s.media.allowSeek
        this[Keys.MEDIA_INTRO_MS] = s.media.collapseAfterIntroMs

        this[Keys.CALL_INCOMING] = s.calls.showIncoming
        this[Keys.CALL_ONGOING] = s.calls.showOngoing
        this[Keys.CALL_CONTROLS] = s.calls.showControls
        this[Keys.CALL_HANDLE] = s.calls.showHandle

        this[Keys.BATTERY_CHARGING] = s.battery.showCharging
        this[Keys.BATTERY_FULL] = s.battery.showFullCharge
        this[Keys.BATTERY_LOW] = s.battery.showLowBattery
        this[Keys.BATTERY_ANIMATION] = s.battery.chargingAnimation
        this[Keys.BATTERY_WATTAGE] = s.battery.showWattage
        this[Keys.BATTERY_THRESHOLD_ENABLED] = s.battery.thresholdEnabled
        this[Keys.BATTERY_THRESHOLD_PERCENT] = s.battery.thresholdPercent
        this[Keys.BATTERY_LOW_PERCENT] = s.battery.lowBatteryPercent

        this[Keys.DEVICE_BLUETOOTH] = s.devices.bluetoothEvents
        this[Keys.DEVICE_WIRED] = s.devices.wiredHeadsetEvents
        this[Keys.DEVICE_WATCH] = s.devices.watchEvents
        this[Keys.DEVICE_CAR] = s.devices.carEvents
        this[Keys.DEVICE_ALLOWED_KINDS] = encodeKinds(s.devices.allowedKinds)
        this[Keys.DEVICE_ALARMS] = s.devices.alarmEvents
        this[Keys.DEVICE_ALARM_LEAD_MIN] = s.devices.alarmLeadTimeMinutes

        this[Keys.BEHAVIOR_TAP] = s.behavior.tapAction.name
        this[Keys.BEHAVIOR_DOUBLE_TAP] = s.behavior.doubleTapAction.name
        this[Keys.BEHAVIOR_LONG_PRESS] = s.behavior.longPressAction.name
        this[Keys.BEHAVIOR_SWIPE_H] = s.behavior.horizontalSwipeAction.name
        this[Keys.BEHAVIOR_SWIPE_UP] = s.behavior.swipeUpAction.name
        this[Keys.BEHAVIOR_SWIPE_DOWN] = s.behavior.swipeDownAction.name
        this[Keys.BEHAVIOR_AUTO_COLLAPSE_S] = s.behavior.autoCollapseSeconds
        this[Keys.BEHAVIOR_FOLLOW_DND] = s.behavior.followSystemDnd
        this[Keys.BEHAVIOR_CRITICAL_DND] = s.behavior.allowCriticalDuringDnd
        this[Keys.BEHAVIOR_LANDSCAPE] = s.behavior.landscapeBehavior.name
        this[Keys.BEHAVIOR_HIDE_SCREEN_OFF] = s.behavior.hideWhenScreenOff
        this[Keys.BEHAVIOR_SHOW_LOCK_SCREEN] = s.behavior.showOnLockScreen
        this[Keys.BEHAVIOR_HAPTICS] = s.behavior.hapticFeedback
        this[Keys.BEHAVIOR_REDUCE_MOTION] = s.behavior.reduceMotion
        this[Keys.BEHAVIOR_POSITION] = s.behavior.position.name
        this[Keys.BEHAVIOR_CUSTOM_V_OFFSET] = s.behavior.customVerticalOffsetDp
        this[Keys.BEHAVIOR_KEEP_MINI] = s.behavior.keepPersistentMiniIsland

        this[Keys.PRIVACY_HIDE_SENSITIVE_TEXT] = s.privacy.hideSensitiveText
        this[Keys.PRIVACY_LOCK_SCREEN_MODE] = s.privacy.lockScreenMode.name
        this[Keys.PRIVACY_HISTORY_ENABLED] = s.privacy.historyEnabled
        this[Keys.PRIVACY_HISTORY_CONTENT] = s.privacy.historyStoresContent
        this[Keys.PRIVACY_HISTORY_RETENTION_DAYS] = s.privacy.historyRetentionDays

        this[Keys.ADVANCED_DEBUG] = s.advanced.debugMode
        this[Keys.ADVANCED_EVENT_LOGGER] = s.advanced.eventLoggerEnabled
        this[Keys.ADVANCED_DEMO] = s.advanced.demoMode
        this[Keys.ADVANCED_PERF_MONITOR] = s.advanced.performanceMonitor
    }

    // endregion

    private inline fun <reified T : Enum<T>> enum(value: String?, fallback: T): T {
        if (value == null) return fallback
        return runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
    }

    private fun encodeAppRules(rules: Map<String, AppNotificationRule>): String {
        val array = JSONArray()
        rules.values.forEach { rule ->
            array.put(
                JSONObject().apply {
                    put("pkg", rule.packageName)
                    put("label", rule.appLabel ?: JSONObject.NULL)
                    put("mode", rule.mode.name)
                },
            )
        }
        return array.toString()
    }

    private fun decodeAppRules(raw: String?): Map<String, AppNotificationRule> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val array = JSONArray(raw)
            buildMap {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val pkg = obj.optString("pkg").takeIf { it.isNotBlank() } ?: continue
                    val mode = runCatching { enumValueOf<AppNotificationMode>(obj.optString("mode")) }
                        .getOrDefault(AppNotificationMode.ALWAYS)
                    val label = obj.optString("label").takeIf { it.isNotBlank() && it != "null" }
                    put(pkg, AppNotificationRule(pkg, label, mode))
                }
            }
        }.getOrElse {
            logger.w(TAG, "app rules unreadable, ignoring", it)
            emptyMap()
        }
    }

    private fun encodeSeenApps(apps: Map<String, String>): String {
        val obj = JSONObject()
        apps.forEach { (pkg, label) -> obj.put(pkg, label) }
        return obj.toString()
    }

    private fun decodeSeenApps(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    val label = obj.optString(key)
                    if (key.isNotBlank()) put(key, label)
                }
            }
        }.getOrElse {
            logger.w(TAG, "seen apps unreadable, ignoring", it)
            emptyMap()
        }
    }

    private fun encodeKinds(kinds: Set<ConnectedDeviceKind>): String =
        JSONArray(kinds.map { it.name }).toString()

    private fun decodeKinds(raw: String?): Set<ConnectedDeviceKind>? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (i in 0 until array.length()) {
                    runCatching { enumValueOf<ConnectedDeviceKind>(array.optString(i)) }
                        .getOrNull()
                        ?.let { add(it) }
                }
            }
        }.getOrNull()
    }

    companion object {
        private const val TAG = "SettingsRepo"

        /** Sentinel for "no custom accent" (DataStore has no nullable primitive support). */
        const val ACCENT_UNSET = Long.MIN_VALUE
    }
}
