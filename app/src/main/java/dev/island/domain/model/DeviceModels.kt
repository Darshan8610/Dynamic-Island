package dev.island.domain.model

/** Power source reported by `BatteryManager.EXTRA_PLUGGED`. */
enum class PlugType {
    NONE,
    AC,
    USB,
    WIRELESS,
    DOCK,
    ;

    val isPlugged: Boolean get() = this != NONE
}

/**
 * Battery/charging snapshot. Only values Android actually publishes are modelled:
 * [wattage] stays null unless voltage and current are both available.
 */
data class ChargingInfo(
    val levelPercent: Int,
    val plugged: PlugType = PlugType.NONE,
    val isCharging: Boolean = false,
    val voltageMv: Int? = null,
    val currentMicroAmps: Int? = null,
    val temperatureTenthsCelsius: Int? = null,
    val batteryHealth: BatteryHealth = BatteryHealth.UNKNOWN,
) {
    /** Computed power in watts, or null when the device does not report both values. */
    val wattage: Float?
        get() {
            val v = voltageMv ?: return null
            val c = currentMicroAmps ?: return null
            if (v <= 0 || c == 0) return null
            val watts = (v.toDouble() / 1000.0) * (kotlin.math.abs(c.toDouble()) / 1_000_000.0)
            // Ignore implausible readings some OEMs report while idle.
            return if (watts in 0.1..240.0) watts.toFloat() else null
        }

    val temperatureCelsius: Float?
        get() = temperatureTenthsCelsius?.let { it / 10f }
}

enum class BatteryHealth { UNKNOWN, GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, FAILURE, COLD }

/** Discrete battery events the user can opt into. */
data class BatteryInfo(
    val levelPercent: Int,
    val isCharging: Boolean,
    val plugged: PlugType = PlugType.NONE,
    val isLow: Boolean = false,
    val isCritical: Boolean = false,
    val isFull: Boolean = false,
    /** Set when the user's "notify at N%" threshold was just crossed. */
    val thresholdReachedPercent: Int? = null,
)

enum class ConnectedDeviceKind {
    WIRED_HEADSET,
    BLUETOOTH_AUDIO,
    WATCH,
    CAR,
    SPEAKER,
    OTHER,
}

/**
 * A device connection change. Names require BLUETOOTH_CONNECT; when that permission is
 * missing [name] is null and the island falls back to a generic label.
 */
data class ConnectedDeviceInfo(
    val kind: ConnectedDeviceKind,
    val connected: Boolean,
    val name: String? = null,
    val address: String? = null,
)
