package dev.island.feature.island.ui.widgets

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Display formatting for island content.
 *
 * The domain hands renderers raw numbers (millis, bytes, watts, metres) and never pre-formatted
 * text, so the UI stays in charge of presentation and every renderer formats identically. All
 * functions are pure, which makes them trivially unit-testable.
 */
object IslandFormat {

    /** `mm:ss`, or `h:mm:ss` past an hour. Rounds up so "0:00" only shows at the real end. */
    fun clock(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val totalSeconds = (safe + 999L) / 1000L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
        } else {
            "%02d:%02d".format(Locale.US, minutes, seconds)
        }
    }

    /** Stopwatch precision: `mm:ss.d`. */
    fun clockWithTenths(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val tenths = (safe % 1000L) / 100L
        val totalSeconds = safe / 1000L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d.%d".format(Locale.US, hours, minutes, seconds, tenths)
        } else {
            "%02d:%02d.%d".format(Locale.US, minutes, seconds, tenths)
        }
    }

    /** Lap/split prefix: `LAP 3`. */
    fun lapIndex(index: Int): String = "LAP ${index + 1}"

    fun percent(value: Int): String = "${value.coerceIn(0, 100)}%"

    fun wattage(watts: Float?): String? =
        watts?.takeIf { it > 0.05f }?.let { "%.1fW".format(Locale.US, it) }

    fun temperature(tenthsCelsius: Int?): String? =
        tenthsCelsius?.let { "%.1f°C".format(Locale.US, it / 10f) }

    fun voltage(millivolts: Int?): String? =
        millivolts?.takeIf { it > 0 }?.let { "%.2fV".format(Locale.US, it / 1000f) }

    fun current(microAmps: Int?): String? =
        microAmps?.takeIf { abs(it) > 1000 }?.let { "%.2fA".format(Locale.US, it / 1_000_000f) }

    fun bytes(value: Long?): String? {
        if (value == null || value < 0L) return null
        val kb = value / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> "%.1f GB".format(Locale.US, gb)
            mb >= 1.0 -> "%.0f MB".format(Locale.US, mb)
            kb >= 1.0 -> "%.0f KB".format(Locale.US, kb)
            else -> "$value B"
        }
    }

    fun speed(bytesPerSecond: Long?): String? =
        bytesPerSecond?.takeIf { it > 0L }?.let { "${bytes(it)}/s" }

    /** Distance for navigation: metres below a kilometre, one decimal above. */
    fun distance(meters: Int?): String? {
        if (meters == null || meters < 0) return null
        return if (meters < 1000) {
            val rounded = (meters / 10.0).roundToInt() * 10
            "${rounded.coerceAtLeast(10)} m"
        } else {
            "%.1f km".format(Locale.US, meters / 1000.0)
        }
    }

    fun eta(minutes: Int?): String? =
        minutes?.takeIf { it >= 0 }?.let { if (it < 60) "$it min" else "${it / 60}h ${it % 60}m" }

    /** `1 of 4` for grouped notifications. */
    fun groupCount(current: Int, total: Int): String? =
        if (total > 1) "$current/$total" else null
}
