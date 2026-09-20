package dev.island.data.notifications

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.island.core.logging.IslandLogger
import dev.island.core.logging.Redaction
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.NotificationInfo
import dev.island.domain.repository.SettingsRepository
import dev.island.domain.repository.recordSeenApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Pipeline stage between [IslandNotificationListener] and [dev.island.domain.engine.IslandEngine]:
 *
 * ```
 * NotificationListenerService → NotificationRepository → IslandEngine
 * ```
 *
 * Responsibilities: keep the set of live notifications, group them per app, turn them into island
 * events, remember which apps the user might want to control, and expose the two operations the
 * island UI needs (cancel a notification, fire one of its actions).
 *
 * Privacy: content is kept in memory only for as long as the notification is live, is never
 * persisted here (history is a separate, opt-in store) and is never logged.
 */
class NotificationRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val logger: IslandLogger,
    private val factory: NotificationEventFactory,
    private val settingsRepository: SettingsRepository,
) {

    /** Latest framework objects, keyed by notification key. Needed to fire source actions. */
    private val live = LinkedHashMap<String, StatusBarNotification>()
    private val parsed = LinkedHashMap<String, NotificationInfo>()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var listener: NotificationListenerService? = null
    private var settings: IslandSettings = IslandSettings.Default
    private var settingsJob: kotlinx.coroutines.Job? = null

    /** Set by the dispatcher: where normalised events go. */
    var onEvent: ((IslandEvent) -> Unit)? = null

    /** Set by the dispatcher: called with the island coalesce key when a notification goes away. */
    var onEventRemoved: ((String) -> Unit)? = null

    fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = scope.launch {
            settingsRepository.settings.collect { next ->
                val previous = settings
                settings = next
                if (previous.notifications != next.notifications || previous.media != next.media) {
                    republishAll()
                }
            }
        }
    }

    fun stopObserving() {
        settingsJob?.cancel()
        settingsJob = null
    }

    fun bind(listener: NotificationListenerService) {
        this.listener = listener
        _connected.value = true
        observeSettings()
        syncFromSystem()
        logger.i(TAG, "notification listener connected")
    }

    fun unbind() {
        listener = null
        _connected.value = false
        stopObserving()
        live.clear()
        parsed.clear()
        logger.i(TAG, "notification listener disconnected")
    }

    /** Re-reads everything the system already knows (used right after the listener connects). */
    fun syncFromSystem() {
        val service = listener ?: return
        val current = runCatching { service.activeNotifications }.getOrNull()
        if (current == null) {
            logger.w(TAG, "activeNotifications unavailable")
            return
        }
        live.clear()
        parsed.clear()
        current.forEach { sbn -> onPosted(sbn, initialSync = true) }
        logger.d(TAG, "synced ${current.size} active notification(s)")
    }

    fun onPosted(sbn: StatusBarNotification, initialSync: Boolean = false) {
        val key = sbn.key ?: return
        live[key] = sbn
        val info = runCatching { factory.toInfo(sbn) }
            .onFailure { logger.w(TAG, "unparsable notification from ${Redaction.safePackage(sbn.packageName)}", it) }
            .getOrNull() ?: return
        parsed[key] = info
        rememberApp(info)
        publish(info, initialSync)
    }

    fun onRemoved(sbn: StatusBarNotification) {
        val key = sbn.key ?: return
        val info = parsed.remove(key)
        live.remove(key)
        if (info == null) return

        val siblings = parsed.values.filter { NotificationGrouping.bucketKey(it) == NotificationGrouping.bucketKey(info) }
        if (siblings.isEmpty()) {
            onEventRemoved?.invoke("notif:" + NotificationGrouping.bucketKey(info))
            logger.d(TAG, "notification removed pkg=${Redaction.safePackage(info.packageName)}")
            return
        }
        // The group shrank: refresh the island entry instead of dropping it.
        publish(siblings.first(), initialSync = false)
    }

    /** Ranking changed (importance, DND, grouping). Cheap refresh, no animation churn. */
    fun onRankingUpdate() = republishAll()

    private fun publish(info: NotificationInfo, initialSync: Boolean) {
        if (!settings.notifications.enabled) return
        val siblings = parsed.values.filter { NotificationGrouping.bucketKey(it) == NotificationGrouping.bucketKey(info) }
        val groups = NotificationGrouping.group(siblings, settings.notifications.groupNotifications)
        val group = groups.firstOrNull { group -> group.items.any { it.key == info.key } || group.representative.key == info.key }
        val representative = group?.representative ?: info

        val event = runCatching {
            factory.toEvent(representative.copy(groupSize = group?.size ?: 1), group, settings, System.currentTimeMillis())
        }.onFailure { logger.w(TAG, "event creation failed", it) }.getOrNull() ?: return

        if (initialSync && !representative.isOngoing) {
            // Re-syncing after a reconnect must not replay history as new events.
            return
        }
        onEvent?.invoke(event)
    }

    private fun republishAll() {
        parsed.values.toList().forEach { publish(it, initialSync = false) }
    }

    private fun rememberApp(info: NotificationInfo) {
        if (info.packageName == context.packageName) return
        scope.launch {
            runCatching { settingsRepository.recordSeenApp(info.packageName, info.appName) }
                .onFailure { logger.w(TAG, "seen app record failed", it) }
        }
    }

    // region island actions

    /** Cancels the notification in the shade as well as on the island. */
    fun cancelNotification(notificationKey: String) {
        val service = listener
        if (service == null) {
            // Without listener access we can only drop the island entry.
            parsed.remove(notificationKey)
            return
        }
        runCatching { service.cancelNotification(notificationKey) }
            .onFailure { logger.w(TAG, "cancelNotification failed", it) }
    }

    /** Fires one of the notification's own actions (reply, mark as read, snooze…). */
    fun fireSourceAction(notificationKey: String, actionIndex: Int) {
        val sbn = live[notificationKey] ?: run {
            logger.d(TAG, "source action ignored: notification no longer live")
            return
        }
        val actions = runCatching { sbn.notification.actions }.getOrNull().orEmpty()
        val action = actions.getOrNull(actionIndex) ?: return
        runCatching { action.actionIntent?.send() }
            .onFailure { logger.w(TAG, "source action failed", it) }
    }

    /** Opens the app that produced the event. */
    fun contentIntentFor(notificationKey: String): android.app.PendingIntent? =
        live[notificationKey]?.notification?.contentIntent

    val activeNotifications: List<NotificationInfo> get() = parsed.values.toList()

    // endregion

    companion object {
        private const val TAG = "NotificationRepo"
    }
}
