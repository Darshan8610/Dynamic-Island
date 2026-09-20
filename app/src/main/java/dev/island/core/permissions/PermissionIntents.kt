package dev.island.core.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.island.core.logging.IslandLogger

/**
 * Every permission screen Island may need to send the user to, built in one place.
 *
 * Each factory returns an [Intent] that is verified with `resolveActivity`-free `runCatching`
 * at launch time ([PermissionLauncher]), because OEM builds routinely remove or rename system
 * settings activities. Nothing here requests a permission silently.
 */
object PermissionIntents {

    fun overlaySettings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun notificationListenerSettings(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun appDetailsSettings(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * The battery-optimisation *list*, not the direct "ignore optimisations" dialog:
     * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is restricted by Play policy for apps that do
     * not have a core user-visible need, and Island's overlay is already a foreground service.
     */
    fun batteryOptimizationSettings(context: Context): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun exactAlarmSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            appDetailsSettings(context)
        }

    fun bluetoothSettings(context: Context): Intent =
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationChannelSettings(context: Context, channelId: String): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * OEM "autostart"/"battery saver" screens. These are undocumented, differ per vendor and are
     * frequently missing, so each one is attempted only after the generic path and every failure
     * is swallowed. Island shows vendor-specific *text* instructions when no intent resolves.
     */
    fun oemBatteryIntents(context: Context): List<Intent> {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val candidates: List<Pair<String, String>> = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> listOf(
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
                "com.miui.powerkeeper" to "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
            )

            manufacturer.contains("samsung") -> listOf(
                "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
                "com.samsung.android.sm" to "com.samsung.android.sm.ui.battery.BatteryActivity",
            )

            manufacturer.contains("oneplus") -> listOf(
                "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            )

            manufacturer.contains("oppo") || manufacturer.contains("realme") -> listOf(
                "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
                "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            )

            manufacturer.contains("vivo") -> listOf(
                "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            )

            manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
                "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            )

            else -> emptyList()
        }
        return candidates.map { (pkg, cls) ->
            Intent().apply {
                component = ComponentName(pkg, cls)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}

/** Launches a permission intent, falling back to app details when the target activity is absent. */
class PermissionLauncher(private val logger: IslandLogger) {

    fun launch(context: Context, intent: Intent): Boolean {
        val attempts = listOf(intent) + PermissionIntents.oemBatteryIntents(context).takeIf {
            intent.action == Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        }.orEmpty() + listOf(PermissionIntents.appDetailsSettings(context))

        attempts.forEach { candidate ->
            val started = runCatching {
                context.startActivity(candidate)
            }.isSuccess
            if (started) return true
        }
        logger.w(TAG, "no settings activity could be launched")
        return false
    }

    /**
     * Android 8+ requires the notification-listener component to be re-requested after the user
     * toggles access. Rebinding is safe and cheap; it never prompts the user by itself.
     */
    fun rebindNotificationListener(context: Context) {
        runCatching {
            val component = ComponentName(context, AndroidPermissionRepository.NOTIFICATION_LISTENER_CLASS)
            android.service.notification.NotificationListenerService.requestRebind(component)
        }.onFailure { logger.w(TAG, "listener rebind failed", it) }
    }

    companion object {
        private const val TAG = "PermissionLauncher"
    }
}
