package dev.island.core

import android.app.Application
import android.content.Intent
import android.provider.Settings
import dev.island.core.logging.IslandLogger
import dev.island.service.IslandService
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Its only jobs are to build the object graph before any other component runs, and to restore the
 * island if it was enabled when the process died (Android may kill the process at any time; the
 * overlay must come back without the user reopening the app).
 *
 * No analytics, no crash-reporting SDK, no initialisation of anything that phones home: Island's
 * process starts, does what the user asked, and stays quiet otherwise.
 */
class IslandApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        logger.i(TAG, "process started")
        restoreIslandIfEnabled()
    }

    /**
     * Re-attaches the overlay after a process restart.
     *
     * Starting a foreground service from the background is refused on Android 12+ unless the app is
     * exempt, so the attempt is guarded: when it is refused, nothing breaks — the island comes back
     * as soon as the user opens the app, or after the next boot broadcast (which IS an exemption).
     */
    private fun restoreIslandIfEnabled() {
        if (!Settings.canDrawOverlays(this)) return
        AppGraph.scope.launch {
            val settings = runCatching { AppGraph.settingsRepository.current() }.getOrNull() ?: return@launch
            if (!settings.islandEnabled) return@launch
            if (AppGraph.serviceRunning.value) return@launch
            logger.i(TAG, "restoring island after process start")
            runCatching {
                startService(Intent(this@IslandApplication, IslandService::class.java).setAction(IslandService.ACTION_START))
            }.onFailure { logger.w(TAG, "background restore refused; will retry when the app opens", it) }
        }
    }

    companion object {
        private const val TAG = "IslandApp"
        private val logger: IslandLogger get() = AppGraph.logger
    }
}
