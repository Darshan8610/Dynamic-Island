package dev.island.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.island.MainActivity
import dev.island.R
import dev.island.domain.model.TimerInfo
import dev.island.service.IslandService
import dev.island.service.TimerActionReceiver

/**
 * Island's own notifications: the foreground-service notification, timer state and the
 * "Island is partially disabled" recovery prompt.
 *
 * Three channels so the user can silence the boring ones and keep the timer alarm:
 * - [CHANNEL_SERVICE]  low importance, silent, ongoing
 * - [CHANNEL_TIMERS]   low importance, ongoing timer/stopwatch progress
 * - [CHANNEL_ALERTS]   high importance, timer completion + permission recovery
 */
object IslandNotifications {

    const val CHANNEL_SERVICE = "island_service"
    const val CHANNEL_TIMERS = "island_timers"
    const val CHANNEL_ALERTS = "island_alerts"
    const val GROUP_ID = "island"

    const val ID_SERVICE = 1001
    const val ID_RECOVERY = 1002
    const val ID_TIMER_BASE = 2000

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val group = NotificationChannelGroup(GROUP_ID, context.getString(R.string.notification_group_name))
        runCatching { manager.createNotificationChannelGroup(group) }

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.channel_service_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_service_description)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            setGroup(GROUP_ID)
        }

        val timers = NotificationChannel(
            CHANNEL_TIMERS,
            context.getString(R.string.channel_timers_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_timers_description)
            setShowBadge(false)
            setGroup(GROUP_ID)
        }

        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alerts_description)
            enableVibration(true)
            enableLights(true)
            setGroup(GROUP_ID)
        }

        runCatching { manager.createNotificationChannels(listOf(service, timers, alerts)) }
    }

    fun notificationIdForTimer(timerId: String): Int =
        ID_TIMER_BASE + (timerId.hashCode() and 0x3FF)

    /** Ongoing foreground-service notification. Content intent opens the dashboard. */
    fun serviceNotification(context: Context, statusLine: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            pendingFlags(),
        )
        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, IslandService::class.java).setAction(IslandService.ACTION_TURN_OFF),
            pendingFlags(),
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_island_status)
            .setContentTitle(context.getString(R.string.service_notification_title))
            .setContentText(statusLine)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.service_action_turn_off), stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setColor(context.getColor(R.color.island_accent))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /** Ongoing countdown notification; the island is not a substitute for this on Android. */
    fun timerRunningNotification(context: Context, timer: TimerInfo, remainingText: String): Notification {
        val timerId = timer.timerId
        val pauseResume = timerAction(context, timerId, TimerActionReceiver.ACTION_TOGGLE_TIMER, requestCode = 10)
        val stop = timerAction(context, timerId, TimerActionReceiver.ACTION_STOP_TIMER, requestCode = 11)

        val label = timer.label?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.timer_notification_default_label)

        return NotificationCompat.Builder(context, CHANNEL_TIMERS)
            .setSmallIcon(R.drawable.ic_island_status)
            .setContentTitle(label)
            .setContentText(remainingText)
            .setContentIntent(openAppIntent(context, timerId))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setColor(context.getColor(R.color.island_accent))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(
                0,
                if (timer.running) context.getString(R.string.action_pause) else context.getString(R.string.action_resume),
                pauseResume,
            )
            .addAction(0, context.getString(R.string.action_stop), stop)
            .build()
    }

    /** Completion notification: audible + vibrate, with stop/dismiss. */
    fun timerFinishedNotification(context: Context, timer: TimerInfo): Notification {
        val timerId = timer.timerId
        val dismiss = timerAction(context, timerId, TimerActionReceiver.ACTION_DISMISS_TIMER, requestCode = 12)
        val restart = timerAction(context, timerId, TimerActionReceiver.ACTION_RESTART_TIMER, requestCode = 13)

        val label = timer.label?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.timer_notification_default_label)

        return NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_island_status)
            .setContentTitle(context.getString(R.string.timer_complete_title))
            .setContentText(label)
            .setContentIntent(openAppIntent(context, timerId))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(false)
            .setColor(context.getColor(R.color.island_accent))
            .addAction(0, context.getString(R.string.action_restart), restart)
            .addAction(0, context.getString(R.string.action_dismiss), dismiss)
            .build()
    }

    /** "Island is partially disabled — Notification access was revoked. Fix". */
    fun recoveryNotification(context: Context, title: String, body: String, fixIntent: Intent): Notification {
        val pending = PendingIntent.getActivity(context, 20, fixIntent, pendingFlags())
        return NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_island_status)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(context.getColor(R.color.island_accent))
            .build()
    }

    fun post(context: Context, id: Int, notification: Notification) {
        // POST_NOTIFICATIONS is optional: if it is missing we simply do not post.
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!allowed) return
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }

    private fun timerAction(context: Context, timerId: String, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode + timerId.hashCode(),
            Intent(context, TimerActionReceiver::class.java).apply {
                this.action = action
                putExtra(TimerActionReceiver.EXTRA_TIMER_ID, timerId)
            },
            pendingFlags() or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openAppIntent(context: Context, timerId: String): PendingIntent = PendingIntent.getActivity(
        context,
        30 + timerId.hashCode(),
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_TIMER, timerId)
        },
        pendingFlags(),
    )

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
}
