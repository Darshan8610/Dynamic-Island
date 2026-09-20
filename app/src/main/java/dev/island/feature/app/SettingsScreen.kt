package dev.island.feature.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.island.R
import dev.island.core.AppGraph
import dev.island.domain.model.AnimationStyle
import dev.island.domain.model.IslandPosition
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.LandscapeBehavior
import dev.island.domain.model.NotificationImportance
import dev.island.domain.model.PrivacyMode
import dev.island.feature.app.ui.ClickRow
import dev.island.feature.app.ui.EnumChoiceRow
import dev.island.feature.app.ui.IslandScaffold
import dev.island.feature.app.ui.PermissionRow
import dev.island.feature.app.ui.RowDivider
import dev.island.feature.app.ui.SectionHeader
import dev.island.feature.app.ui.SettingsCard
import dev.island.feature.app.ui.SliderRow
import dev.island.feature.app.ui.ToggleRow
import dev.island.feature.app.ui.rememberSettingsUpdate
import kotlinx.coroutines.launch
import dev.island.service.IslandService

/**
 * Settings: every user-facing switch in one place, each one wired straight to DataStore.
 *
 * Rules this screen follows:
 * - a setting that needs a system permission shows the permission row instead of pretending to work;
 * - a setting that only makes sense with another one on is disabled, not hidden, so the user can see
 *   why (and what to turn on);
 * - nothing is written anywhere except the local DataStore file.
 */
@Composable
fun SettingsScreen(
    settings: IslandSettings,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val update = rememberSettingsUpdate()
    val permissions by AppGraph.permissionRepository.statuses.collectAsState(initial = emptyList())

    val positionLabels = listOf(
        stringResource(R.string.position_auto),
        stringResource(R.string.position_top_center),
        stringResource(R.string.position_lower),
        stringResource(R.string.position_custom),
    )
    val privacyLabels = listOf(
        stringResource(R.string.privacy_full),
        stringResource(R.string.privacy_private),
        stringResource(R.string.privacy_icon_only),
        stringResource(R.string.privacy_sensitive_hidden),
    )
    val importanceLabels = listOf(
        stringResource(R.string.importance_none),
        stringResource(R.string.importance_min),
        stringResource(R.string.importance_low),
        stringResource(R.string.importance_default),
        stringResource(R.string.importance_high),
        stringResource(R.string.importance_urgent),
    )
    val landscapeLabels = listOf(
        stringResource(R.string.landscape_adapt),
        stringResource(R.string.landscape_minimize),
        stringResource(R.string.landscape_hide),
    )
    val animationLabels = listOf(
        stringResource(R.string.animation_springy),
        stringResource(R.string.animation_smooth),
        stringResource(R.string.animation_snappy),
    )
    val speedLabels = listOf(
        stringResource(R.string.speed_half),
        stringResource(R.string.speed_three_quarter),
        stringResource(R.string.speed_one),
        stringResource(R.string.speed_one_quarter_more),
        stringResource(R.string.speed_one_half),
    )
    val tapLabels = listOf(
        stringResource(R.string.tap_expand),
        stringResource(R.string.tap_expand_or_open),
        stringResource(R.string.tap_open_app),
    )
    val doubleTapLabels = listOf(
        stringResource(R.string.double_tap_toggle),
        stringResource(R.string.double_tap_dismiss),
        stringResource(R.string.double_tap_cycle),
    )
    val longPressLabels = listOf(
        stringResource(R.string.long_press_settings),
        stringResource(R.string.long_press_open_app),
        stringResource(R.string.long_press_dismiss),
        stringResource(R.string.long_press_pin),
    )
    val swipeLabels = listOf(
        stringResource(R.string.swipe_cycle),
        stringResource(R.string.swipe_dismiss),
        stringResource(R.string.swipe_none),
    )
    val verticalSwipeLabels = listOf(
        stringResource(R.string.swipe_expand),
        stringResource(R.string.swipe_dismiss),
        stringResource(R.string.swipe_none),
    )

    IslandScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            // region island
            item {
                SectionHeader(text = stringResource(R.string.settings_section_island))
                SettingsCard {
                    ToggleRow(
                        title = stringResource(R.string.settings_island_enable),
                        summary = stringResource(R.string.settings_island_enable_summary),
                        checked = settings.islandEnabled,
                        onCheckedChange = { enabled ->
                            update { it.copy(islandEnabled = enabled) }
                            if (enabled) IslandService.start(context)
                        },
                    )
                    RowDivider()
                    EnumChoiceRow(
                        title = stringResource(R.string.settings_island_position),
                        summary = stringResource(R.string.settings_island_position_summary),
                        values = IslandPosition.entries.toList(),
                        labels = positionLabels,
                        selected = settings.behavior.position,
                        onSelect = { position -> update { it.copy(behavior = it.behavior.copy(position = position)) } },
                    )
                    RowDivider()
                    SliderRow(
                        title = stringResource(R.string.settings_island_auto_collapse),
                        value = settings.behavior.autoCollapseSeconds.toFloat(),
                        onValueChange = { seconds ->
                            update { it.copy(behavior = it.behavior.copy(autoCollapseSeconds = seconds.toInt())) }
                        },
                        valueRange = 2f..20f,
                        steps = 17,
                        label = stringResource(R.string.duration_seconds, settings.behavior.autoCollapseSeconds),
                    )
                    RowDivider()
                    ClickRow(
                        title = stringResource(R.string.customize_title),
                        summary = stringResource(R.string.customize_body),
                        onClick = { onNavigate(Routes.CUSTOMIZE) },
                    )
                }
            }
            // endregion

            // region notifications
            item {
                SectionHeader(text = stringResource(R.string.settings_section_notifications))
                SettingsCard {
                    ToggleRow(
                        title = stringResource(R.string.settings_notifications_enable),
                        checked = settings.notifications.enabled,
                        onCheckedChange = { on -> update { it.copy(notifications = it.notifications.copy(enabled = on)) } },
                    )
                    RowDivider()
                    permissions
                        .firstOrNull { it.key == dev.island.domain.model.PermissionKey.NOTIFICATION_LISTENER }
                        ?.let { status -> PermissionRow(status = status) }
                    RowDivider()
                    EnumChoiceRow(
                        title = stringResource(R.string.settings_notifications_privacy),
                        values = PrivacyMode.entries.toList(),
                        labels = privacyLabels,
                        selected = settings.notifications.privacyMode,
                        onSelect = { mode ->
                            update { it.copy(notifications = it.notifications.copy(privacyMode = mode)) }
                        },
                    )
                    RowDivider()
                    EnumChoiceRow(
                        title = stringResource(R.string.settings_notifications_min_importance),
                        values = NotificationImportance.entries.toList(),
                        labels = importanceLabels,
                        selected = settings.notifications.minImportance,
                        onSelect = { importance ->
                            update { it.copy(notifications = it.notifications.copy(minImportance = importance)) }
                        },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_notifications_grouping),
                        summary = stringResource(R.string.settings_notifications_grouping_summary),
                        checked = settings.notifications.groupNotifications,
                        onCheckedChange = { on ->
                            update { it.copy(notifications = it.notifications.copy(groupNotifications = on)) }
                        },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_notifications_ongoing),
                        checked = settings.notifications.showOngoingNotifications,
                        onCheckedChange = { on ->
                            update { it.copy(notifications = it.notifications.copy(showOngoingNotifications = on)) }
                        },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_notifications_expand_high),
                        checked = settings.notifications.expandOnHighImportance,
                        onCheckedChange = { on ->
                            update { it.copy(notifications = it.notifications.copy(expandOnHighImportance = on)) }
                        },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_notifications_hide_sensitive),
                        summary = stringResource(R.string.settings_notifications_hide_sensitive_summary),
                        checked = settings.notifications.hideSensitiveContent,
                        onCheckedChange = { on ->
                            update { it.copy(notifications = it.notifications.copy(hideSensitiveContent = on)) }
                        },
                    )
                    RowDivider()
                    ClickRow(
                        title = stringResource(R.string.settings_notifications_per_app),
                        summary = "${settings.notifications.seenApps.size} ${stringResource(R.string.per_app_body)}",
                        onClick = { onNavigate(Routes.PER_APP) },
                    )
                }
            }
            // endregion

            // region media
            item {
                SectionHeader(text = stringResource(R.string.settings_section_media))
                SettingsCard {
                    ToggleRow(
                        title = stringResource(R.string.settings_media_enable),
                        checked = settings.media.enabled,
                        onCheckedChange = { on -> update { it.copy(media = it.media.copy(enabled = on)) } },
                    )
                    ToggleRow(
                        title = stringResource(R.string.settings_media_art),
                        checked = settings.media.showAlbumArt,
                        onCheckedChange = { on -> update { it.copy(media = it.media.copy(showAlbumArt = on)) } },
                        enabled = settings.media.enabled,
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_media_controls),
                        checked = settings.media.showTransportControls,
                        onCheckedChange = { on -> update { it.copy(media = it.media.copy(showTransportControls = on)) } },
                        enabled = settings.media.enabled,
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_media_progress),
                        checked = settings.media.showProgressBar,
                        onCheckedChange = { on -> update { it.copy(media = it.media.copy(showProgressBar = on)) } },
                        enabled = settings.media.enabled,
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_media_waveform),
                        checked = settings.media.showCollapsedWaveform,
                        onCheckedChange = { on -> update { it.copy(media = it.media.copy(showCollapsedWaveform = on)) } },
                        enabled = settings.media.enabled,
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_media_seek),
                        checked = settings.media.allowSeek,
                        onCheckedChange = { on -> update { it.copy(media = it.media.copy(allowSeek = on)) } },
                        enabled = settings.media.enabled,
                    )
                }
            }
            // endregion

            // region calls
            item {
                SectionHeader(text = stringResource(R.string.settings_section_calls))
                SettingsCard {
                    ToggleRow(
                        title = stringResource(R.string.settings_calls_incoming),
                        checked = settings.calls.showIncoming,
                        onCheckedChange = { on -> update { it.copy(calls = it.calls.copy(showIncoming = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_calls_ongoing),
                        checked = settings.calls.showOngoing,
                        onCheckedChange = { on -> update { it.copy(calls = it.calls.copy(showOngoing = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_calls_controls),
                        summary = stringResource(R.string.settings_calls_controls_summary),
                        checked = settings.calls.showControls,
                        onCheckedChange = { on -> update { it.copy(calls = it.calls.copy(showControls = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_calls_handle),
                        checked = settings.calls.showHandle,
                        onCheckedChange = { on -> update { it.copy(calls = it.calls.copy(showHandle = on)) } },
                    )
                    RowDivider()
                    permissions
                        .filter {
                            it.key == dev.island.domain.model.PermissionKey.READ_PHONE_STATE ||
                                it.key == dev.island.domain.model.PermissionKey.ANSWER_PHONE_CALLS
                        }
                        .forEachIndexed { index, status ->
                            if (index > 0) RowDivider()
                            PermissionRow(status = status)
                        }
                }
            }
            // endregion

            // region battery & devices
            item {
                SectionHeader(text = stringResource(R.string.settings_section_battery))
                SettingsCard {
                    ToggleRow(
                        title = stringResource(R.string.settings_battery_charging),
                        checked = settings.battery.showCharging,
                        onCheckedChange = { on -> update { it.copy(battery = it.battery.copy(showCharging = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_battery_full),
                        checked = settings.battery.showFullCharge,
                        onCheckedChange = { on -> update { it.copy(battery = it.battery.copy(showFullCharge = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_battery_low),
                        checked = settings.battery.showLowBattery,
                        onCheckedChange = { on -> update { it.copy(battery = it.battery.copy(showLowBattery = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_battery_animation),
                        checked = settings.battery.chargingAnimation,
                        onCheckedChange = { on -> update { it.copy(battery = it.battery.copy(chargingAnimation = on)) } },
                        enabled = settings.behavior.reduceMotion.not(),
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_battery_wattage),
                        summary = stringResource(R.string.settings_battery_wattage_summary),
                        checked = settings.battery.showWattage,
                        onCheckedChange = { on -> update { it.copy(battery = it.battery.copy(showWattage = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_battery_threshold),
                        checked = settings.battery.thresholdEnabled,
                        onCheckedChange = { on -> update { it.copy(battery = it.battery.copy(thresholdEnabled = on)) } },
                    )
                    RowDivider()
                    SliderRow(
                        title = stringResource(R.string.settings_battery_threshold_percent),
                        value = settings.battery.thresholdPercent.toFloat(),
                        onValueChange = { value ->
                            update { it.copy(battery = it.battery.copy(thresholdPercent = value.toInt())) }
                        },
                        valueRange = 50f..100f,
                        steps = 49,
                        label = stringResource(R.string.value_percent, settings.battery.thresholdPercent),
                    )
                    RowDivider()
                    SliderRow(
                        title = stringResource(R.string.settings_battery_low_percent),
                        value = settings.battery.lowBatteryPercent.toFloat(),
                        onValueChange = { value ->
                            update { it.copy(battery = it.battery.copy(lowBatteryPercent = value.toInt())) }
                        },
                        valueRange = 5f..30f,
                        steps = 24,
                        label = stringResource(R.string.value_percent, settings.battery.lowBatteryPercent),
                    )
                }
            }

            item {
                SectionHeader(text = stringResource(R.string.settings_section_devices))
                SettingsCard {
                    ToggleRow(
                        title = stringResource(R.string.settings_devices_bluetooth),
                        checked = settings.devices.bluetoothEvents,
                        onCheckedChange = { on -> update { it.copy(devices = it.devices.copy(bluetoothEvents = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_devices_wired),
                        checked = settings.devices.wiredHeadsetEvents,
                        onCheckedChange = { on -> update { it.copy(devices = it.devices.copy(wiredHeadsetEvents = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_devices_watch),
                        checked = settings.devices.watchEvents,
                        onCheckedChange = { on -> update { it.copy(devices = it.devices.copy(watchEvents = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_devices_car),
                        checked = settings.devices.carEvents,
                        onCheckedChange = { on -> update { it.copy(devices = it.devices.copy(carEvents = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_devices_alarms),
                        checked = settings.devices.alarmEvents,
                        onCheckedChange = { on -> update { it.copy(devices = it.devices.copy(alarmEvents = on)) } },
                    )
                    RowDivider()
                    SliderRow(
                        title = stringResource(R.string.settings_devices_alarm_lead),
                        value = settings.devices.alarmLeadTimeMinutes.toFloat(),
                        onValueChange = { value ->
                            update { it.copy(devices = it.devices.copy(alarmLeadTimeMinutes = value.toInt())) }
                        },
                        valueRange = 1f..60f,
                        steps = 58,
                        label = stringResource(R.string.duration_minutes_short, settings.devices.alarmLeadTimeMinutes),
                    )
                    RowDivider()
                    permissions
                        .filter {
                            it.key == dev.island.domain.model.PermissionKey.BLUETOOTH_CONNECT ||
                                it.key == dev.island.domain.model.PermissionKey.EXACT_ALARM
                        }
                        .forEachIndexed { index, status ->
                            if (index > 0) RowDivider()
                            PermissionRow(status = status)
                        }
                }
            }
            // endregion

            // region behavior
            item {
                SectionHeader(text = stringResource(R.string.settings_section_behavior))
                SettingsCard {
                    EnumChoiceRow(
                        title = stringResource(R.string.settings_behavior_tap),
                        values = dev.island.domain.model.TapAction.entries.toList(),
                        labels = tapLabels,
                        selected = settings.behavior.tapAction,
                        onSelect = { action -> update { it.copy(behavior = it.behavior.copy(tapAction = action)) } },
                    )
                    RowDivider()
                    EnumChoiceRow(
                        title = stringResource(R.string.settings_behavior_double_tap),
                        values = dev.island.domain.model.DoubleTapAction.entries.toList(),
                        labels = doubleTapLabels,
                        selected = settings.behavior.doubleTapAction,
                        onSelect = { action ->
                            update { it.copy(behavior = it.behavior.copy(doubleTapAction = action)) }
                        },
                    )
                    RowDivider()
                    EnumChoiceRow(
                        title = stringResource(R.string.settings_behavior_long_press),
                        values = dev.island.domain.model.LongPressAction.entries.toList(),
                        labels = longPressLabels,
                        selected = settings.behavior.longPressAction,
                        onSelect = { action ->
                            update { it.copy(behavior = it.behavior.copy(longPressAction = action)) }
                        },
                    )
                    RowDivider()
                    EnumChoiceRow(
                        title = stringResource(R.string.settings_behavior_swipe_horizontal),
                        values = dev.island.domain.model.HorizontalSwipeAction.entries.toList(),
                        labels = swipeLabels,
                        selected = settings.behavior.horizontalSwipeAction,
                        onSelect = { action ->
                            update { it.copy(behavior = it.behavior.copy(horizontalSwipeAction = action)) }
                        },
                    )
                    RowDivider()
                    EnumChoiceRow(
                        title = stringResource(R.string.settings_behavior_swipe_up),
                        values = dev.island.domain.model.VerticalSwipeAction.entries.toList(),
                        labels = verticalSwipeLabels,
                        selected = settings.behavior.swipeUpAction,
                        onSelect = { action -> update { it.copy(behavior = it.behavior.copy(swipeUpAction = action)) } },
                    )
                    RowDivider()
                    EnumChoiceRow(
                        title = stringResource(R.string.settings_behavior_swipe_down),
                        values = dev.island.domain.model.VerticalSwipeAction.entries.toList(),
                        labels = verticalSwipeLabels,
                        selected = settings.behavior.swipeDownAction,
                        onSelect = { action ->
                            update { it.copy(behavior = it.behavior.copy(swipeDownAction = action)) }
                        },
                    )
                    RowDivider()
                    EnumChoiceRow(
                        title = stringResource(R.string.settings_behavior_landscape),
                        values = LandscapeBehavior.entries.toList(),
                        labels = landscapeLabels,
                        selected = settings.behavior.landscapeBehavior,
                        onSelect = { behavior ->
                            update { it.copy(behavior = it.behavior.copy(landscapeBehavior = behavior)) }
                        },
                    )
                    RowDivider()
                    EnumChoiceRow(
                        title = stringResource(R.string.settings_island_animation_style),
                        values = AnimationStyle.entries.toList(),
                        labels = animationLabels,
                        selected = settings.appearance.animationStyle,
                        onSelect = { style -> update { it.copy(appearance = it.appearance.copy(animationStyle = style)) } },
                    )
                    RowDivider()
                    EnumChoiceRow(
                        title = stringResource(R.string.settings_island_animation_intensity),
                        values = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f),
                        labels = speedLabels,
                        selected = settings.appearance.animationSpeed,
                        onSelect = { speed ->
                            update { it.copy(appearance = it.appearance.copy(animationSpeed = speed)) }
                        },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_behavior_dnd),
                        checked = settings.behavior.followSystemDnd,
                        onCheckedChange = { on -> update { it.copy(behavior = it.behavior.copy(followSystemDnd = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_behavior_dnd_critical),
                        checked = settings.behavior.allowCriticalDuringDnd,
                        onCheckedChange = { on ->
                            update { it.copy(behavior = it.behavior.copy(allowCriticalDuringDnd = on)) }
                        },
                        enabled = settings.behavior.followSystemDnd,
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_behavior_screen_off),
                        checked = settings.behavior.hideWhenScreenOff,
                        onCheckedChange = { on ->
                            update { it.copy(behavior = it.behavior.copy(hideWhenScreenOff = on)) }
                        },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_behavior_lock_screen),
                        summary = stringResource(R.string.settings_behavior_lock_screen_summary),
                        checked = settings.behavior.showOnLockScreen,
                        onCheckedChange = { on -> update { it.copy(behavior = it.behavior.copy(showOnLockScreen = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_behavior_mini_island),
                        checked = settings.behavior.keepPersistentMiniIsland,
                        onCheckedChange = { on ->
                            update { it.copy(behavior = it.behavior.copy(keepPersistentMiniIsland = on)) }
                        },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_behavior_haptics),
                        checked = settings.behavior.hapticFeedback,
                        onCheckedChange = { on -> update { it.copy(behavior = it.behavior.copy(hapticFeedback = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_behavior_reduce_motion),
                        summary = stringResource(R.string.settings_behavior_reduce_motion_summary),
                        checked = settings.behavior.reduceMotion,
                        onCheckedChange = { on -> update { it.copy(behavior = it.behavior.copy(reduceMotion = on)) } },
                    )
                }
            }
            // endregion

            // region privacy
            item {
                SectionHeader(text = stringResource(R.string.settings_section_privacy))
                SettingsCard {
                    EnumChoiceRow(
                        title = stringResource(R.string.settings_privacy_lock_screen),
                        summary = stringResource(R.string.settings_privacy_body),
                        values = PrivacyMode.entries.toList(),
                        labels = privacyLabels,
                        selected = settings.privacy.lockScreenMode,
                        onSelect = { mode -> update { it.copy(privacy = it.privacy.copy(lockScreenMode = mode)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_privacy_history),
                        summary = stringResource(R.string.settings_privacy_history_summary),
                        checked = settings.privacy.historyEnabled,
                        onCheckedChange = { on -> update { it.copy(privacy = it.privacy.copy(historyEnabled = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_privacy_history_content),
                        summary = stringResource(R.string.settings_privacy_history_content_summary),
                        checked = settings.privacy.historyStoresContent,
                        onCheckedChange = { on ->
                            update { it.copy(privacy = it.privacy.copy(historyStoresContent = on)) }
                        },
                        enabled = settings.privacy.historyEnabled,
                    )
                    RowDivider()
                    SliderRow(
                        title = stringResource(R.string.settings_privacy_history_retention),
                        value = settings.privacy.historyRetentionDays.toFloat(),
                        onValueChange = { days ->
                            update { it.copy(privacy = it.privacy.copy(historyRetentionDays = days.toInt())) }
                        },
                        valueRange = 1f..30f,
                        steps = 28,
                        label = "${settings.privacy.historyRetentionDays}d",
                    )
                    RowDivider()
                    ClickRow(
                        title = stringResource(R.string.settings_privacy_clear),
                        summary = stringResource(R.string.history_clear),
                        onClick = { scope.launch { AppGraph.historyRepository.clear() } },
                    )
                }
            }
            // endregion

            // region advanced
            item {
                SectionHeader(text = stringResource(R.string.settings_section_advanced))
                SettingsCard {
                    ToggleRow(
                        title = stringResource(R.string.settings_advanced_demo),
                        summary = stringResource(R.string.demo_body),
                        checked = settings.advanced.demoMode,
                        onCheckedChange = { on -> update { it.copy(advanced = it.advanced.copy(demoMode = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_advanced_debug),
                        checked = settings.advanced.debugMode,
                        onCheckedChange = { on -> update { it.copy(advanced = it.advanced.copy(debugMode = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_advanced_logger),
                        checked = settings.advanced.eventLoggerEnabled,
                        onCheckedChange = { on ->
                            update { it.copy(advanced = it.advanced.copy(eventLoggerEnabled = on)) }
                        },
                        enabled = settings.advanced.debugMode,
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_advanced_perf),
                        checked = settings.advanced.performanceMonitor,
                        onCheckedChange = { on ->
                            update { it.copy(advanced = it.advanced.copy(performanceMonitor = on)) }
                        },
                        enabled = settings.advanced.debugMode,
                    )
                    RowDivider()
                    ClickRow(
                        title = stringResource(R.string.event_log_title),
                        summary = stringResource(R.string.event_log_body),
                        onClick = { onNavigate(Routes.EVENT_LOG) },
                        enabled = settings.advanced.debugMode,
                    )
                    RowDivider()
                    ClickRow(
                        title = stringResource(R.string.perf_title),
                        summary = stringResource(R.string.perf_body),
                        onClick = { onNavigate(Routes.PERF) },
                        enabled = settings.advanced.debugMode,
                    )
                    RowDivider()
                    ClickRow(
                        title = stringResource(R.string.diagnostics_title),
                        summary = stringResource(R.string.diagnostics_body),
                        onClick = { onNavigate(Routes.DIAGNOSTICS) },
                    )
                    RowDivider()
                    ClickRow(
                        title = stringResource(R.string.settings_advanced_reset),
                        summary = stringResource(R.string.settings_advanced_reset_confirm),
                        onClick = { scope.launch { AppGraph.settingsRepository.reset() } },
                    )
                }
            }
            // endregion

            item {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
}

