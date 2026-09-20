package dev.island.data.device

import android.app.NotificationManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.island.core.logging.IslandLogger
import dev.island.domain.model.DeviceContext
import dev.island.domain.repository.DeviceStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Live device context: screen on/off, keyguard, Do Not Disturb, orientation and battery summary.
 *
 * Everything is callback driven — broadcast receivers, a `zen_mode` content observer and
 * component callbacks. There is no polling loop and no wake lock anywhere in this class.
 */
class DeviceStateRepositoryImpl(
    private val context: Context,
    private val scope: CoroutineScope,
    private val logger: IslandLogger,
    private val batteryMonitor: BatteryMonitor,
) : DeviceStateRepository {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _deviceContext = MutableStateFlow(readNow(DeviceContext()))
    private val state: StateFlow<DeviceContext> = _deviceContext.asStateFlow()

    override val deviceContext = state

    private var started = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_USER_PRESENT,
                -> publish("screen broadcast ${intent.action}")
            }
        }
    }

    private val dndObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) = publish("zen_mode changed")
    }

    private val componentCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) = publish("configuration changed")
        override fun onLowMemory() = Unit
    }

    override fun start() {
        if (started) return
        started = true
        runCatching {
            ContextCompat.registerReceiver(
                context,
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { logger.w(TAG, "screen receiver failed", it) }

        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(ZEN_MODE),
                false,
                dndObserver,
            )
        }.onFailure { logger.w(TAG, "zen observer failed", it) }

        runCatching { context.registerComponentCallbacks(componentCallbacks) }
            .onFailure { logger.w(TAG, "component callbacks failed", it) }

        // Battery summary feeds the same context; collected once, pushed on change only.
        scope.launch {
            batteryMonitor.snapshots.collect { charging ->
                val current = _deviceContext.value
                val next = current.copy(
                    batteryPercent = charging.levelPercent,
                    isCharging = charging.isCharging,
                )
                if (next != current) _deviceContext.value = next
            }
        }
        publish("device monitor started")
    }

    override fun stop() {
        if (!started) return
        started = false
        runCatching { context.unregisterReceiver(screenReceiver) }
        runCatching { context.contentResolver.unregisterContentObserver(dndObserver) }
        runCatching { context.unregisterComponentCallbacks(componentCallbacks) }
        logger.d(TAG, "device monitor stopped")
    }

    /** Re-reads every source; called on start and after configuration changes. */
    fun refresh() {
        publish("manual refresh")
    }

    private fun publish(reason: String) {
        val next = readNow()
        if (next != _deviceContext.value) {
            _deviceContext.value = next
            logger.v(TAG, "device context: $reason -> $next")
        }
    }

    private fun readNow(fallback: DeviceContext = _deviceContext.value): DeviceContext {
        val screenOn = powerManager?.isInteractive ?: true
        val locked = keyguardManager?.isKeyguardLocked ?: false
        val dnd = readInterruptionFilter()
        val landscape = readLandscape()
        val charging = runCatching { batteryMonitor.current() }.getOrNull()
        return DeviceContext(
            screenOn = screenOn,
            screenLocked = locked,
            doNotDisturb = dnd,
            landscape = landscape,
            batteryPercent = charging?.levelPercent ?: fallback.batteryPercent,
            isCharging = charging?.isCharging ?: fallback.isCharging,
            isPowerSaveMode = powerManager?.isPowerSaveMode ?: false,
            isInteractiveDisplay = screenOn,
        )
    }

    /**
     * Do Not Disturb. Island reads the system state and *follows* it — it never changes it and
     * never bypasses the system's own notification suppression.
     */
    private fun readInterruptionFilter(): Boolean {
        val nm = notificationManager ?: return false
        return runCatching {
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrDefault(false)
    }

    private fun readLandscape(): Boolean {
        val orientation = context.resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) return true
        // Large screens (tablets, unfolded foldables) are treated as landscape-capable layouts.
        val smallest = context.resources.configuration.smallestScreenWidthDp
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            orientation == Configuration.ORIENTATION_UNDEFINED &&
            smallest >= TABLET_SMALLEST_WIDTH_DP
    }

    companion object {
        private const val TAG = "DeviceState"
        private const val ZEN_MODE = "zen_mode"
        private const val TABLET_SMALLEST_WIDTH_DP = 600
    }
}
