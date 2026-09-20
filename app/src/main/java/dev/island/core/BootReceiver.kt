package dev.island.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.island.service.IslandService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores Island after a reboot or an app update.
 *
 * Only the user's own preference is restored: if Island was switched on before the reboot, it is
 * switched on again (BOOT_COMPLETED is one of the few cases where Android allows a foreground
 * service to be started from the background). Nothing else is scheduled, no job is registered, and
 * no work happens when the island is off — a device that reboots into Island-disabled stays exactly
 * as quiet as it was.
 *
 * LOCKED_BOOT_COMPLETED fires before the user unlocks the device, where credential-encrypted
 * storage (and therefore DataStore) is still unreadable; that path is guarded and simply skipped,
 * because BOOT_COMPLETED follows it.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        val appContext = context.applicationContext
        AppGraph.init(appContext)
        val logger = AppGraph.logger

        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            logger.d(TAG, "locked boot: settings not readable yet, waiting for BOOT_COMPLETED")
            return
        }

        val pendingResult = goAsync()
        AppGraph.scope.launch(Dispatchers.Main.immediate) {
            runCatching {
                if (!Settings.canDrawOverlays(appContext)) {
                    logger.i(TAG, "boot restore skipped: overlay permission not granted")
                    return@runCatching
                }
                val settings = AppGraph.settingsRepository.current()
                if (!settings.islandEnabled) {
                    logger.i(TAG, "boot restore skipped: island is off")
                    return@runCatching
                }
                logger.i(TAG, "boot restore: starting island")
                IslandService.start(appContext)
            }.onFailure { logger.w(TAG, "boot restore failed", it) }
            pendingResult.finish()
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
