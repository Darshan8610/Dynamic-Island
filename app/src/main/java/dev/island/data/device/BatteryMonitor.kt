package dev.island.data.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.os.BatteryManager
import dev.island.core.logging.IslandLogger
import dev.island.domain.model.BatteryHealth
import dev.island.domain.model.ChargingInfo
import dev.island.domain.model.PlugType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Battery + charging state.
 *
 * `ACTION_BATTERY_CHANGED` is a sticky system broadcast: registering a receiver immediately
 * delivers the current state and then every change. No polling, no wake locks. Power in watts is
 * only computed when the device actually reports voltage *and* current — most do not.
 */
class BatteryMonitor(private val context: Context, private val logger: IslandLogger) {

    /** Emits on every system battery broadcast; distinct values only. */
    val snapshots: Flow<ChargingInfo> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val snapshot = parse(intent) ?: return
                trySend(snapshot)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        runCatching {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
            .onFailure { logger.w(TAG, "battery receiver registration failed", it) }

        // Seed with the sticky value so subscribers do not wait for the next 1% change.
        runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.let { sticky -> parse(sticky)?.let { trySend(it) } }
        }.onFailure { logger.w(TAG, "sticky battery read failed", it) }

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
                .onFailure { logger.d(TAG, "battery receiver already unregistered") }
        }
    }.distinctUntilChanged()

    /** Instantaneous read for diagnostics; returns null when the platform has no data yet. */
    fun current(): ChargingInfo? = runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { parse(it) }
    }.getOrNull()

    private fun parse(intent: Intent): ChargingInfo? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val percent = ((level * 100) / scale).coerceIn(0, 100)

        val plugged = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
            // BatteryManager.BATTERY_PLUGGED_DOCK (API 31). Inlined constant: safe on API 26+.
            PLUGGED_DOCK -> PlugType.DOCK
            else -> PlugType.NONE
        }

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || plugged.isPlugged

        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0).takeIf { it > 0 }
        val current = readCurrent(intent)
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0).takeIf { it > 0 }

        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.FAILURE
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
            else -> BatteryHealth.UNKNOWN
        }

        return ChargingInfo(
            levelPercent = percent,
            plugged = plugged,
            isCharging = isCharging,
            voltageMv = voltage,
            currentMicroAmps = current,
            temperatureTenthsCelsius = temperature,
            batteryHealth = health,
        )
    }

    /**
     * `EXTRA_CURRENT_NOW` is public but not part of the documented contract on every OEM, so it
     * is read reflectively-free but defensively: absent or absurd values are dropped.
     */
    private fun readCurrent(intent: Intent): Int? {
        val raw = intent.getIntExtra(EXTRA_CURRENT_NOW, Int.MIN_VALUE)
        if (raw == Int.MIN_VALUE) return null
        val magnitude = kotlin.math.abs(raw)
        // Sensible range for a phone battery: 1 mA … 10 A.
        return if (magnitude in 1_000..10_000_000) raw else null
    }

    companion object {
        private const val TAG = "BatteryMonitor"
        private const val EXTRA_CURRENT_NOW = "current_now"

        /** Value of `BatteryManager.BATTERY_PLUGGED_DOCK`; declared here to stay API 26 safe. */
        private const val PLUGGED_DOCK = 4
    }
}
