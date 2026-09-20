package dev.island.data.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.island.core.AppGraph

/**
 * Notification monitoring. The user enables this explicitly in system settings; Island shows an
 * explanation before sending them there and reports the state on the dashboard.
 *
 * The service is intentionally thin: it forwards framework callbacks to
 * [NotificationRepository], which owns parsing, grouping and privacy. All heavy work happens off
 * the binder thread (the repository dispatches into the app's coroutine scope).
 *
 * When access is revoked, [onListenerDisconnected] fires and the repository clears its state; the
 * rest of Island (media, timers, charging, Bluetooth, calls) keeps working.
 */
class IslandNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        AppGraph.notificationRepository.bind(this)
        AppGraph.onNotificationAccessChanged(granted = true)
    }

    override fun onListenerDisconnected() {
        AppGraph.notificationRepository.unbind()
        AppGraph.onNotificationAccessChanged(granted = false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        AppGraph.notificationRepository.onPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        AppGraph.notificationRepository.onRemoved(sbn)
    }

    /** Importance/grouping/DND changes land here; no per-notification work is needed. */
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        super.onNotificationRankingUpdate(rankingMap)
        AppGraph.notificationRepository.onRankingUpdate()
    }
}
