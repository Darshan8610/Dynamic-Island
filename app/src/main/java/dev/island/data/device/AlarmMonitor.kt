package dev.island.data.device

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dev.island.core.logging.IslandLogger
import dev.island.domain.engine.IslandClock
import dev.island.domain.model.AlarmInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

/**
 * System alarm clock state via [AlarmManager.getNextAlarmClock].
 *
 * This is the only alarm API a normal app may use: Island reads the next alarm's trigger time and
 * the package that owns it. It cannot read the alarm's label, cannot dismiss or snooze another
 * app's alarm, and does not register alarms of its own here (timers use [dev.island.data.timers]).
 * A *ringing* alarm is surfaced through the alarm app's own notification (category "alarm"),
 * which the notification pipeline promotes to HIGH priority.
 */
class AlarmMonitor(
    private val context: Context,
    private val logger: IslandLogger,
    private val clock: IslandClock,
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /** Emits whenever the system reports that the next alarm changed. */
    val changes: Flow<AlarmInfo?> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(current())
            }
        }
        runCatching {
            context.registerReceiver(receiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED))
        }.onFailure { logger.w(TAG, "alarm receiver registration failed", it) }

        trySend(current())

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
                .onFailure { logger.d(TAG, "alarm receiver already unregistered") }
        }
    }

    /** Next alarm within [leadMinutes], or null. */
    fun upcoming(leadMinutes: Int): AlarmInfo? {
        val info = current() ?: return null
        val minutes = info.minutesUntil(clock.wallClockMs())
        return if (minutes in 0..leadMinutes) info else null
    }

    fun current(): AlarmInfo? {
        val manager = alarmManager ?: return null
        return runCatching {
            val next = manager.nextAlarmClock ?: return@runCatching null
            val triggerAt = next.triggerTime
            if (triggerAt <= 0L) return@runCatching null
            val showIntentPackage = runCatching { next.showIntent?.creatorPackage }.getOrNull()
            AlarmInfo(
                label = null, // Android does not expose the alarm label to other apps.
                triggerAtMs = triggerAt,
                showIntentPackage = showIntentPackage,
                isRinging = false,
                canSnooze = false,
            )
        }.onFailure { logger.w(TAG, "next alarm read failed", it) }.getOrNull()
    }

    /** Human readable trigger time for the collapsed pill, e.g. "07:30". */
    fun formatTriggerTime(info: AlarmInfo): String =
        android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(info.triggerAtMs))

    companion object {
        private const val TAG = "AlarmMonitor"
    }
}

/** Convenience: only non-null alarms that are still in the future. */
fun Flow<AlarmInfo?>.filterFuture(clock: IslandClock): Flow<AlarmInfo?> = map { info ->
    if (info != null && info.triggerAtMs > clock.wallClockMs()) info else null
}
