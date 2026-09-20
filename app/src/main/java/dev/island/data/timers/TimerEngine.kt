package dev.island.data.timers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.island.core.logging.IslandLogger
import dev.island.core.notifications.IslandNotifications
import dev.island.domain.engine.IslandClock
import dev.island.domain.model.StopwatchInfo
import dev.island.domain.model.TimerInfo
import dev.island.domain.repository.TimerRepository
import dev.island.service.TimerActionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Countdown timers + stopwatch.
 *
 * Timing rules:
 * - every duration is derived from [IslandClock.elapsedMs] (monotonic), so a wall-clock change
 *   can never make a timer jump;
 * - completion is scheduled twice: an in-process `delay` while the app is alive, and an
 *   `AlarmManager` alarm so completion survives process death (exact when the user granted
 *   `SCHEDULE_EXACT_ALARM`, inexact otherwise — Island never abuses the alarm permission);
 * - state is persisted to a small JSON file so a restart restores running timers;
 * - there is no tick loop: the UI projects the remaining time from the monotonic sample, which
 *   is why a running timer costs nothing while the island is hidden.
 */
class TimerEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val clock: IslandClock,
    private val logger: IslandLogger,
) : TimerRepository {

    /** Internal record; [endsAtWallMs] is only used to survive a reboot. */
    data class TimerRecord(
        val id: String,
        val label: String?,
        val totalMs: Long,
        val remainingMs: Long,
        val running: Boolean,
        val finished: Boolean,
        val sampledAtElapsedMs: Long,
        val endsAtWallMs: Long?,
        val createdAtMs: Long,
    )

    private val _records = MutableStateFlow<List<TimerRecord>>(emptyList())
    private val _stopwatch = MutableStateFlow<StopwatchInfo?>(null)
    private val completionJobs = HashMap<String, Job>()
    private var started = false
    private var persistJob: Job? = null

    private val stopwatchFile: File get() = File(context.filesDir, STOPWATCH_FILE)
    private val timersFile: File get() = File(context.filesDir, TIMERS_FILE)

    override val timers = _records.asStateFlow().map { records -> records.map { it.toInfo() } }
    override val stopwatch: StateFlow<StopwatchInfo?> = _stopwatch.asStateFlow()

    /** Live snapshot for the overlay/service without collecting a flow. */
    val timerSnapshot: List<TimerInfo> get() = _records.value.map { it.toInfo() }
    val stopwatchSnapshot: StopwatchInfo? get() = _stopwatch.value

    val runningTimerCount: Int get() = _records.value.count { it.running }

    override fun start() {
        if (started) return
        started = true
        restore()
        _records.value.forEach { record -> if (record.running) scheduleCompletion(record) }
        logger.i(TAG, "timer engine started with ${_records.value.size} timer(s)")
    }

    // region timers

    override suspend fun createTimer(durationMs: Long, label: String?): String {
        if (durationMs <= 0L) return ""
        if (_records.value.size >= MAX_TIMERS) {
            logger.w(TAG, "timer limit reached ($MAX_TIMERS)")
            return ""
        }
        val id = UUID.randomUUID().toString()
        val now = clock.elapsedMs()
        val record = TimerRecord(
            id = id,
            label = label,
            totalMs = durationMs,
            remainingMs = durationMs,
            running = true,
            finished = false,
            sampledAtElapsedMs = now,
            endsAtWallMs = clock.wallClockMs() + durationMs,
            createdAtMs = clock.wallClockMs(),
        )
        _records.value = _records.value + record
        scheduleCompletion(record)
        persist()
        updateTimerNotification(record)
        logger.i(TAG, "timer created id=$id duration=${durationMs}ms")
        return id
    }

    override suspend fun pauseTimer(timerId: String) = mutate(timerId) { record ->
        val remaining = record.remainingAt(clock.elapsedMs())
        cancelCompletion(record.id)
        record.copy(running = false, remainingMs = remaining, endsAtWallMs = null, sampledAtElapsedMs = clock.elapsedMs())
    }

    override suspend fun resumeTimer(timerId: String) = mutate(timerId) { record ->
        if (record.finished) return@mutate record
        val resumed = record.copy(
            running = true,
            sampledAtElapsedMs = clock.elapsedMs(),
            endsAtWallMs = clock.wallClockMs() + record.remainingAt(clock.elapsedMs()),
        )
        scheduleCompletion(resumed)
        resumed
    }

    /** Toggle used by the island's pause/resume action and the notification action. */
    override suspend fun toggleTimer(timerId: String) {
        val record = _records.value.firstOrNull { it.id == timerId } ?: return
        if (record.running) pauseTimer(timerId) else resumeTimer(timerId)
    }

    override suspend fun stopTimer(timerId: String) {
        cancelCompletion(timerId)
        IslandNotifications.cancel(context, IslandNotifications.notificationIdForTimer(timerId))
        _records.value = _records.value.filterNot { it.id == timerId }
        persist()
        logger.i(TAG, "timer stopped id=$timerId")
    }

    /** Keeps a finished timer in the list so the island can show "Timer complete" until dismissed. */
    suspend fun dismissFinished(timerId: String) = stopTimer(timerId)

    suspend fun restartTimer(timerId: String) = mutate(timerId) { record ->
        cancelCompletion(record.id)
        val restarted = record.copy(
            remainingMs = record.totalMs,
            finished = false,
            running = true,
            sampledAtElapsedMs = clock.elapsedMs(),
            endsAtWallMs = clock.wallClockMs() + record.totalMs,
        )
        scheduleCompletion(restarted)
        restarted
    }

    /** Called by [TimerActionReceiver] when the AlarmManager alarm fires (process may be cold). */
    fun onAlarmFired(timerId: String) {
        start()
        val record = _records.value.firstOrNull { it.id == timerId } ?: return
        if (record.finished) return
        complete(record)
    }

    private fun complete(record: TimerRecord) {
        cancelCompletion(record.id)
        _records.value = _records.value.map {
            if (it.id == record.id) {
                it.copy(running = false, finished = true, remainingMs = 0L, endsAtWallMs = null, sampledAtElapsedMs = clock.elapsedMs())
            } else {
                it
            }
        }
        val finished = record.copy(running = false, finished = true, remainingMs = 0L).toInfo()
        IslandNotifications.post(
            context,
            IslandNotifications.notificationIdForTimer(record.id),
            IslandNotifications.timerFinishedNotification(context, finished),
        )
        vibrate()
        persist()
        logger.i(TAG, "timer complete id=${record.id}")
    }

    private fun scheduleCompletion(record: TimerRecord) {
        cancelCompletion(record.id)
        val remaining = record.remainingAt(clock.elapsedMs())
        if (remaining <= 0L) {
            complete(record)
            return
        }
        completionJobs[record.id] = scope.launch {
            delay(remaining)
            val current = _records.value.firstOrNull { it.id == record.id } ?: return@launch
            if (current.running && !current.finished) complete(current)
        }
        scheduleAlarm(record, remaining)
    }

    private fun cancelCompletion(timerId: String) {
        completionJobs.remove(timerId)?.cancel()
        cancelAlarm(timerId)
    }

    private fun scheduleAlarm(record: TimerRecord, remainingMs: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAt = clock.wallClockMs() + remainingMs
        runCatching {
            val pending = alarmPendingIntent(record.id)
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                // Documented fallback: completion may be up to ~15 minutes late on a dozing device.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                logger.w(TAG, "exact alarms unavailable; timer completion is inexact")
            }
        }.onFailure { logger.w(TAG, "alarm scheduling failed", it) }
    }

    private fun cancelAlarm(timerId: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { manager.cancel(alarmPendingIntent(timerId)) }
    }

    private fun alarmPendingIntent(timerId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        4000 + timerId.hashCode(),
        Intent(context, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_TIMER_ALARM
            putExtra(TimerActionReceiver.EXTRA_TIMER_ID, timerId)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private suspend fun mutate(timerId: String, transform: (TimerRecord) -> TimerRecord) {
        var changed: TimerRecord? = null
        _records.value = _records.value.map { record ->
            if (record.id == timerId) transform(record).also { changed = it } else record
        }
        changed?.let {
            persist()
            updateTimerNotification(it)
        }
    }

    private fun updateTimerNotification(record: TimerRecord) {
        if (!record.running || record.finished) {
            IslandNotifications.cancel(context, IslandNotifications.notificationIdForTimer(record.id))
            return
        }
        IslandNotifications.post(
            context,
            IslandNotifications.notificationIdForTimer(record.id),
            IslandNotifications.timerRunningNotification(context, record.toInfo(), formatRemaining(record)),
        )
    }

    private fun formatRemaining(record: TimerRecord): String {
        val ms = record.remainingAt(clock.elapsedMs())
        val totalSeconds = (ms + 999) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun TimerRecord.toInfo(): TimerInfo {
        val remaining = remainingAt(clock.elapsedMs())
        return TimerInfo(
            timerId = id,
            label = label,
            totalMs = totalMs,
            remainingMs = remaining,
            running = running,
            finished = finished,
            sampledAtElapsedMs = if (running) clock.elapsedMs() else sampledAtElapsedMs,
            index = _records.value.indexOfFirst { it.id == id }.coerceAtLeast(0),
            activeTimerCount = _records.value.count { it.running || it.finished },
        )
    }

    private fun TimerRecord.remainingAt(elapsedNowMs: Long): Long {
        if (!running || finished) return remainingMs.coerceAtLeast(0L)
        return (remainingMs - (elapsedNowMs - sampledAtElapsedMs).coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    // endregion

    // region stopwatch

    override suspend fun toggleStopwatch() {
        val current = _stopwatch.value ?: StopwatchInfo()
        val now = clock.elapsedMs()
        _stopwatch.value = if (current.running) {
            current.copy(
                elapsedMs = current.elapsedAt(now),
                running = false,
                sampledAtElapsedMs = now,
            )
        } else {
            current.copy(running = true, sampledAtElapsedMs = now)
        }
        persistStopwatch()
    }

    override suspend fun resetStopwatch() {
        _stopwatch.value = null
        runCatching { if (stopwatchFile.exists()) stopwatchFile.delete() }
    }

    override suspend fun lapStopwatch() {
        val current = _stopwatch.value ?: return
        val now = clock.elapsedMs()
        val elapsed = current.elapsedAt(now)
        _stopwatch.value = current.copy(
            elapsedMs = elapsed,
            sampledAtElapsedMs = now,
            laps = (current.laps + elapsed).takeLast(MAX_LAPS),
        )
        persistStopwatch()
    }

    private fun persistStopwatch() {
        val value = _stopwatch.value ?: return
        scope.launch {
            runCatching {
                stopwatchFile.writeText(
                    JSONObject().apply {
                        put("elapsedMs", value.elapsedMs)
                        put("running", value.running)
                        put("sampledAtElapsedMs", value.sampledAtElapsedMs)
                        put("laps", JSONArray(value.laps))
                    }.toString(),
                )
            }.onFailure { logger.w(TAG, "stopwatch persist failed", it) }
        }
    }

    private fun restoreStopwatch() {
        if (!stopwatchFile.exists()) return
        runCatching {
            val obj = JSONObject(stopwatchFile.readText())
            val laps = obj.optJSONArray("laps")?.let { array ->
                (0 until array.length()).map { array.optLong(it) }
            }.orEmpty()
            _stopwatch.value = StopwatchInfo(
                elapsedMs = obj.optLong("elapsedMs"),
                running = obj.optBoolean("running", false),
                sampledAtElapsedMs = obj.optLong("sampledAtElapsedMs"),
                laps = laps,
            )
        }.onFailure { logger.w(TAG, "stopwatch restore failed", it) }
    }

    // endregion

    // region persistence

    private fun persist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            runCatching {
                val array = JSONArray()
                _records.value.forEach { record ->
                    array.put(
                        JSONObject().apply {
                            put("id", record.id)
                            put("label", record.label ?: JSONObject.NULL)
                            put("totalMs", record.totalMs)
                            put("remainingMs", record.remainingMs)
                            put("running", record.running)
                            put("finished", record.finished)
                            put("sampledAtElapsedMs", record.sampledAtElapsedMs)
                            put("endsAtWallMs", record.endsAtWallMs ?: JSONObject.NULL)
                            put("createdAtMs", record.createdAtMs)
                        },
                    )
                }
                timersFile.writeText(array.toString())
            }.onFailure { logger.w(TAG, "timer persist failed", it) }
        }
    }

    private fun restore() {
        restoreStopwatch()
        if (!timersFile.exists()) return
        val restored = runCatching {
            val array = JSONArray(timersFile.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val record = runCatching {
                        val running = obj.optBoolean("running")
                        val endsAt = obj.optLong("endsAtWallMs").takeIf { it > 0L }
                        // After a reboot elapsedRealtime restarts, so rebase from the wall clock.
                        val remaining = if (running && endsAt != null) {
                            (endsAt - clock.wallClockMs()).coerceAtLeast(0L)
                        } else {
                            obj.optLong("remainingMs")
                        }
                        TimerRecord(
                            id = obj.getString("id"),
                            label = obj.optString("label").takeIf { it.isNotBlank() && it != "null" },
                            totalMs = obj.optLong("totalMs"),
                            remainingMs = remaining,
                            running = running && remaining > 0L,
                            finished = obj.optBoolean("finished") || (running && remaining <= 0L),
                            sampledAtElapsedMs = clock.elapsedMs(),
                            endsAtWallMs = if (running && remaining > 0L) clock.wallClockMs() + remaining else null,
                            createdAtMs = obj.optLong("createdAtMs"),
                        )
                    }.getOrNull()
                    if (record != null) add(record)
                }
            }
        }.onFailure { logger.w(TAG, "timer restore failed", it) }.getOrDefault(emptyList())

        _records.value = restored.take(MAX_TIMERS)
        if (restored.isNotEmpty()) logger.i(TAG, "restored ${restored.size} timer(s)")
    }

    // endregion

    private fun vibrate() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        }.onFailure { logger.d(TAG, "vibration unavailable") }
    }

    companion object {
        private const val TAG = "TimerEngine"
        private const val TIMERS_FILE = "island_timers.json"
        private const val STOPWATCH_FILE = "island_stopwatch.json"
        private const val PERSIST_DEBOUNCE_MS = 400L
        private const val VIBRATION_MS = 500L
        const val MAX_TIMERS = 5
        const val MAX_LAPS = 50
    }
}
