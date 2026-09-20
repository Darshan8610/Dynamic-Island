package dev.island.domain.repository

import dev.island.domain.model.AdvancedSettings
import dev.island.domain.model.AppNotificationMode
import dev.island.domain.model.AppNotificationRule
import dev.island.domain.model.BatterySettings
import dev.island.domain.model.BehaviorSettings
import dev.island.domain.model.CallSettings
import dev.island.domain.model.ConnectedDeviceKind
import dev.island.domain.model.DeviceSettings
import dev.island.domain.model.IslandAppearance
import dev.island.domain.model.IslandPreset
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.MediaSettings
import dev.island.domain.model.NotificationSettings
import dev.island.domain.model.PrivacySettings

/**
 * Typed mutators. Screens never touch preference keys; they call one of these, which keeps
 * every write on a single path ([SettingsRepository.update]) and makes the DataStore transaction
 * atomic per change.
 */
suspend fun SettingsRepository.setIslandEnabled(enabled: Boolean) = update {
    it.copy(islandEnabled = enabled, lastKnownServiceActive = enabled)
}

suspend fun SettingsRepository.completeOnboarding() = update { it.copy(onboardingComplete = true) }

suspend fun SettingsRepository.updateAppearance(transform: (IslandAppearance) -> IslandAppearance) = update {
    it.copy(appearance = transform(it.appearance), advanced = it.advanced)
}

suspend fun SettingsRepository.applyPreset(preset: IslandPreset) = update { current ->
    if (preset == IslandPreset.CUSTOM) {
        current.copy(appearance = current.appearance.copy(preset = IslandPreset.CUSTOM))
    } else {
        current.copy(appearance = preset.apply(current.appearance).copy(preset = preset))
    }
}

suspend fun SettingsRepository.updateNotifications(transform: (NotificationSettings) -> NotificationSettings) =
    update { it.copy(notifications = transform(it.notifications)) }

suspend fun SettingsRepository.updateMedia(transform: (MediaSettings) -> MediaSettings) =
    update { it.copy(media = transform(it.media)) }

suspend fun SettingsRepository.updateCalls(transform: (CallSettings) -> CallSettings) =
    update { it.copy(calls = transform(it.calls)) }

suspend fun SettingsRepository.updateBattery(transform: (BatterySettings) -> BatterySettings) =
    update { it.copy(battery = transform(it.battery)) }

suspend fun SettingsRepository.updateDevices(transform: (DeviceSettings) -> DeviceSettings) =
    update { it.copy(devices = transform(it.devices)) }

suspend fun SettingsRepository.updateBehavior(transform: (BehaviorSettings) -> BehaviorSettings) =
    update { it.copy(behavior = transform(it.behavior)) }

suspend fun SettingsRepository.updatePrivacy(transform: (PrivacySettings) -> PrivacySettings) =
    update { it.copy(privacy = transform(it.privacy)) }

suspend fun SettingsRepository.updateAdvanced(transform: (AdvancedSettings) -> AdvancedSettings) =
    update { it.copy(advanced = transform(it.advanced)) }

suspend fun SettingsRepository.setAppNotificationMode(
    packageName: String,
    appLabel: String?,
    mode: AppNotificationMode,
) = update { current ->
    val rules = current.notifications.appRules.toMutableMap()
    if (mode == AppNotificationMode.ALWAYS) {
        rules.remove(packageName)
    } else {
        rules[packageName] = AppNotificationRule(packageName, appLabel, mode)
    }
    current.copy(notifications = current.notifications.copy(appRules = rules))
}

suspend fun SettingsRepository.setDeviceKindEnabled(kind: ConnectedDeviceKind, enabled: Boolean) =
    update { current ->
        val kinds = current.devices.allowedKinds.toMutableSet()
        if (enabled) kinds += kind else kinds -= kind
        current.copy(devices = current.devices.copy(allowedKinds = kinds))
    }

/** Convenience for diagnostics/permission recovery flows. */
suspend fun SettingsRepository.markServiceState(active: Boolean) = update {
    it.copy(lastKnownServiceActive = active)
}

/** Read-only view used by previews and tests. */
val IslandSettings.isFullyConfigured: Boolean
    get() = onboardingComplete && islandEnabled
