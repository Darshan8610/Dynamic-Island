package dev.island.service

import android.content.Context
import android.content.Intent
import dev.island.MainActivity
import dev.island.core.logging.IslandLogger
import dev.island.data.device.CallStateMonitor
import dev.island.data.notifications.NotificationRepository
import dev.island.domain.model.IslandAction
import dev.island.domain.model.IslandActionKind
import dev.island.domain.model.IslandEvent
import dev.island.domain.repository.IslandActionHandler
import dev.island.domain.repository.MediaRepository
import dev.island.domain.repository.TimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Executes island button presses.
 *
 * Rules:
 * - Island never performs an action the platform does not permit; capabilities are checked first
 *   and the corresponding button is not rendered when the capability is missing.
 * - Answering/ending a call needs ANSWER_PHONE_CALLS; without it only "Open Phone" is offered.
 * - Another app's alarm cannot be dismissed by a third-party app, so alarm actions open the alarm
 *   app instead of pretending to silence it.
 */
class IslandActionRunner(
    private val context: Context,
    private val scope: CoroutineScope,
    private val logger: IslandLogger,
    private val mediaRepository: MediaRepository,
    private val timerRepository: TimerRepository,
    private val notificationRepository: NotificationRepository,
    private val callStateMonitor: CallStateMonitor,
    /**
     * Demo mode makes every control inert except dismissing and opening Island: a simulated event
     * must never reach the user's real media session, phone call or timers.
     */
    private val demoMode: () -> Boolean = { false },
) : IslandActionHandler {

    override suspend fun handle(action: IslandAction, event: IslandEvent) {
        logger.d(TAG, "action ${action.kind} on ${event.type} (${event.id})")
        if (demoMode() && action.kind !in DEMO_SAFE_ACTIONS) {
            logger.d(TAG, "demo mode: ${action.kind} ignored")
            return
        }
        when (action.kind) {
            IslandActionKind.PLAY_PAUSE -> mediaRepository.togglePlayPause()
            IslandActionKind.NEXT -> mediaRepository.skipNext()
            IslandActionKind.PREVIOUS -> mediaRepository.skipPrevious()
            IslandActionKind.SEEK -> action.seekToMs?.let { mediaRepository.seekTo(it) }

            IslandActionKind.ANSWER_CALL -> callStateMonitor.answer()
            IslandActionKind.END_CALL -> callStateMonitor.endCall()
            IslandActionKind.MUTE_CALL -> callStateMonitor.toggleMute()

            IslandActionKind.TIMER_PAUSE_RESUME -> timerId(event)?.let { timerRepository.toggleTimer(it) }
            IslandActionKind.TIMER_STOP -> timerId(event)?.let { timerRepository.stopTimer(it) }
            IslandActionKind.TIMER_RESET -> timerId(event)?.let { timerRepository.stopTimer(it) }
            IslandActionKind.TIMER_SNOOZE -> timerId(event)?.let { timerRepository.stopTimer(it) }

            IslandActionKind.STOPWATCH_LAP -> timerRepository.lapStopwatch()
            IslandActionKind.STOPWATCH_PAUSE_RESUME -> timerRepository.toggleStopwatch()
            IslandActionKind.STOPWATCH_RESET -> timerRepository.resetStopwatch()

            IslandActionKind.ALARM_DISMISS, IslandActionKind.ALARM_SNOOZE -> openAlarmApp(event)

            IslandActionKind.SOURCE_ACTION -> {
                val index = action.sourceActionIndex ?: return
                notificationKey(event)?.let { notificationRepository.fireSourceAction(it, index) }
            }

            IslandActionKind.OPEN_SOURCE_APP -> openSource(event)
            IslandActionKind.OPEN_ISLAND -> openIslandApp()

            IslandActionKind.DISMISS -> {
                // The engine removes the event; additionally cancel the underlying notification so
                // dismissing on the island behaves like dismissing in the shade.
                notificationKey(event)?.let { notificationRepository.cancelNotification(it) }
            }

            IslandActionKind.CUSTOM -> logger.d(TAG, "custom action ignored: ${action.id}")
        }
    }

    private fun timerId(event: IslandEvent): String? = when (event) {
        is IslandEvent.Timer -> event.timer.timerId
        else -> null
    }

    private fun notificationKey(event: IslandEvent): String? = when (event) {
        is IslandEvent.Notification -> event.notification.key
        is IslandEvent.Download -> null
        else -> null
    }

    private suspend fun openSource(event: IslandEvent) {
        // Prefer the notification's own content intent (it lands exactly where the user expects).
        val key = notificationKey(event)
        val contentIntent = key?.let { notificationRepository.contentIntentFor(it) }
        if (contentIntent != null) {
            val sent = withContext(Dispatchers.Main) {
                runCatching { contentIntent.send() }.isSuccess
            }
            if (sent) return
        }
        val pkg = event.sourcePackage ?: return
        withContext(Dispatchers.Main) {
            val intent = runCatching { context.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull()
            if (intent == null) {
                logger.w(TAG, "no launch intent for source app")
                return@withContext
            }
            runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { logger.w(TAG, "opening source app failed", it) }
        }
    }

    private fun openIslandApp() {
        scope.launch(Dispatchers.Main) {
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }.onFailure { logger.w(TAG, "opening Island failed", it) }
        }
    }

    /**
     * A third-party app cannot dismiss or snooze another app's alarm. Island opens the owning
     * alarm app (or the system clock) instead of pretending to control it.
     */
    private fun openAlarmApp(event: IslandEvent) {
        val pkg = (event as? IslandEvent.Alarm)?.alarm?.showIntentPackage
        scope.launch(Dispatchers.Main) {
            val intent = pkg?.let { runCatching { context.packageManager.getLaunchIntentForPackage(it) }.getOrNull() }
                ?: Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { logger.w(TAG, "opening alarm app failed", it) }
        }
    }

    companion object {
        private const val TAG = "ActionRunner"

        /** Actions that stay inside Island, so they are safe even for a simulated event. */
        private val DEMO_SAFE_ACTIONS = setOf(
            IslandActionKind.DISMISS,
            IslandActionKind.OPEN_ISLAND,
        )
    }
}
