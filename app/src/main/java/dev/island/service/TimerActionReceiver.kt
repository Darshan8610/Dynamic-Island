package dev.island.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.island.core.AppGraph
import dev.island.core.notifications.IslandNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles timer notifications' buttons and the AlarmManager completion alarm.
 *
 * A receiver (not the service) because these intents can arrive when the process is cold — the
 * alarm has to fire even if Island was killed. [goAsync] plus the process scope in [AppGraph]
 * gives the coroutine room to finish without the receiver being killed mid-write.
 */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val timerId = intent.getStringExtra(EXTRA_TIMER_ID) ?: return
        val appContext = context.applicationContext

        // Cold start: the graph must exist before anything else touches it.
        AppGraph.init(appContext)
        val timers = AppGraph.timerRepository
        val logger = AppGraph.logger

        val pendingResult = goAsync()
        AppGraph.scope.launch(Dispatchers.Main.immediate) {
            runCatching {
                when (action) {
                    ACTION_TIMER_ALARM -> timers.onAlarmFired(timerId)
                    ACTION_TOGGLE_TIMER -> timers.toggleTimer(timerId)
                    ACTION_STOP_TIMER -> timers.stopTimer(timerId)
                    ACTION_DISMISS_TIMER -> {
                        IslandNotifications.cancel(appContext, IslandNotifications.notificationIdForTimer(timerId))
                        timers.dismissFinished(timerId)
                    }

                    ACTION_RESTART_TIMER -> timers.restartTimer(timerId)
                    else -> logger.d(TAG, "ignoring unknown timer action: $action")
                }
            }.onFailure { logger.w(TAG, "timer action failed: $action", it) }
            pendingResult.finish()
        }
    }

    companion object {
        private const val TAG = "TimerActionReceiver"

        const val ACTION_TIMER_ALARM = "dev.island.action.TIMER_ALARM"
        const val ACTION_TOGGLE_TIMER = "dev.island.action.TOGGLE_TIMER"
        const val ACTION_STOP_TIMER = "dev.island.action.STOP_TIMER"
        const val ACTION_DISMISS_TIMER = "dev.island.action.DISMISS_TIMER"
        const val ACTION_RESTART_TIMER = "dev.island.action.RESTART_TIMER"
        const val EXTRA_TIMER_ID = "dev.island.extra.TIMER_ID"
    }
}
