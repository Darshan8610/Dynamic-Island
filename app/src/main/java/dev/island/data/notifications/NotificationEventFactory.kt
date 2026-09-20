package dev.island.data.notifications

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import dev.island.R
import dev.island.core.logging.IslandLogger
import dev.island.core.logging.Redaction
import dev.island.domain.model.ActionLabelKey
import dev.island.domain.model.DownloadInfo
import dev.island.domain.model.IslandAction
import dev.island.domain.model.IslandActionKind
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandEventMeta
import dev.island.domain.model.IslandEventType
import dev.island.domain.model.IslandIconKey
import dev.island.domain.model.IslandPriority
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.ManeuverKind
import dev.island.domain.model.NavigationInfo
import dev.island.domain.model.NotificationImportance
import dev.island.domain.model.NotificationInfo
import dev.island.domain.model.PrivacyLevel

/**
 * Turns a [StatusBarNotification] into a normalised island event.
 *
 * Everything is read from public notification fields — no accessibility scraping, no screen
 * reading. Media-style notifications are ignored while the media-session bridge is enabled, so
 * the same track never appears twice.
 */
class NotificationEventFactory(
    private val context: Context,
    private val logger: IslandLogger,
) {

    /** Step 1: extract a [NotificationInfo]. Defensive against malformed extras. */
    fun toInfo(sbn: StatusBarNotification): NotificationInfo {
        val notification = sbn.notification
        val extras: Bundle = runCatching { notification.extras }.getOrNull() ?: Bundle.EMPTY
        val packageName = sbn.packageName ?: ""
        val appName = appLabel(packageName)

        val title = extras.charSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.charSequence(Notification.EXTRA_TITLE_BIG)?.toString()
        val text = extras.charSequence(Notification.EXTRA_TEXT)?.toString()
        val subText = extras.charSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val bigText = extras.charSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val template = extras.getString(Notification.EXTRA_TEMPLATE)

        val flags = notification.flags
        val isOngoing = flags and Notification.FLAG_ONGOING_EVENT != 0
        val isGroupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0
        val isForegroundService = flags and Notification.FLAG_FOREGROUND_SERVICE != 0

        val hasProgress = extras.containsKey(Notification.EXTRA_PROGRESS)
        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progressCurrent = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

        val actions = runCatching { notification.actions }.getOrNull().orEmpty()
        val actionLabels = actions.mapNotNull { it.title?.toString()?.takeIf(String::isNotBlank) }

        return NotificationInfo(
            key = sbn.key ?: "",
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            subText = subText,
            bigText = bigText,
            category = notification.category,
            importance = NotificationImportance.from(notification.importance),
            postedAtMs = sbn.postTime,
            isOngoing = isOngoing,
            isGroupSummary = isGroupSummary,
            groupKey = sbn.groupKey,
            groupSize = 1,
            hasProgress = hasProgress,
            progressMax = progressMax,
            progressCurrent = progressCurrent,
            progressIndeterminate = indeterminate,
            actionCount = actions.size,
            actionLabels = actionLabels,
            isMediaStyle = template?.contains("MediaStyle") == true,
            isCallStyle = template?.contains("CallStyle") == true ||
                notification.category == Notification.CATEGORY_CALL,
            visibilityPrivate = notification.visibility == Notification.VISIBILITY_PRIVATE ||
                notification.visibility == Notification.VISIBILITY_SECRET,
            isForegroundService = isForegroundService,
            personCount = personCount(extras),
        )
    }

    /**
     * Step 2: build the island event. Returns null when the notification should not produce an
     * event at all (media handled by the media bridge, empty system noise).
     */
    fun toEvent(
        info: NotificationInfo,
        group: NotificationGroup?,
        settings: IslandSettings,
        nowMs: Long,
    ): IslandEvent? {
        if (info.packageName == context.packageName) return null
        if (info.isMediaStyle && settings.media.enabled) {
            logger.v(TAG, "media-style notification skipped (media bridge owns it)")
            return null
        }
        if (info.key.isBlank()) return null

        val groupSize = group?.size ?: 1
        val coalesceKey = "notif:" + NotificationGrouping.bucketKey(info)
        val createdAt = if (info.postedAtMs > 0L) info.postedAtMs else nowMs

        val baseMeta = IslandEventMeta(
            id = coalesceKey,
            type = IslandEventType.NOTIFICATION,
            priority = IslandPriority.LOW,
            createdAt = createdAt,
            title = info.title ?: info.appName,
            subtitle = info.previewText,
            iconKey = iconKeyFor(info),
            accentArgb = null,
            progress = info.progressFraction,
            actions = actionsFor(info, groupSize),
            expandable = groupSize > 1 || info.actionCount > 0 || !info.bigText.isNullOrBlank() || info.previewText != null,
            persistent = info.isOngoing || info.isForegroundService,
            sourcePackage = info.packageName,
            sourceLabel = info.appName,
            privacyLevel = PrivacyLevel.PUBLIC,
            coalesceKey = coalesceKey,
        )

        // Structured progress notifications become first-class download events.
        if (isDownloadLike(info)) {
            return IslandEvent.Download(
                meta = baseMeta.copy(
                    type = IslandEventType.DOWNLOAD,
                    title = context.getString(R.string.island_downloading),
                    subtitle = info.title ?: info.appName,
                    iconKey = IslandIconKey.DOWNLOAD,
                ),
                download = DownloadInfo(
                    fileName = info.title ?: info.previewText,
                    percent = info.progressFraction?.let { (it * 100).toInt() },
                    downloadedBytes = null,
                    totalBytes = null,
                    bytesPerSecond = null,
                    indeterminate = info.progressIndeterminate,
                    completed = false,
                ),
            )
        }

        val grouped = group?.items.orEmpty().filterNot { it.key == info.key }
        val meta = if (groupSize > 1) {
            baseMeta.copy(
                title = info.appName,
                subtitle = context.resources.getQuantityString(
                    R.plurals.island_grouped_notifications,
                    groupSize,
                    groupSize,
                ),
                expandable = true,
            )
        } else {
            baseMeta
        }

        if (info.category == Notification.CATEGORY_NAVIGATION) {
            val navMeta = meta.copy(type = IslandEventType.NAVIGATION, iconKey = IslandIconKey.NAVIGATION)
            return IslandEvent.Navigation(
                meta = navMeta,
                navigation = NavigationInfo(
                    maneuver = ManeuverKind.UNKNOWN,
                    distanceMeters = null,
                    streetName = navMeta.subtitle,
                    etaMinutes = null,
                    remainingDistanceMeters = null,
                    provider = navMeta.sourceLabel,
                ),
            )
        }

        return IslandEvent.Notification(
            meta = meta,
            notification = info.copy(groupSize = groupSize),
            grouped = grouped,
        )
    }

    private fun isDownloadLike(info: NotificationInfo): Boolean {
        if (!info.hasProgress) return false
        if (info.isMediaStyle || info.isCallStyle) return false
        return info.category == Notification.CATEGORY_PROGRESS ||
            (info.isOngoing && !info.isForegroundService)
    }

    private fun iconKeyFor(info: NotificationInfo): IslandIconKey = when (info.category) {
        Notification.CATEGORY_CALL -> IslandIconKey.CALL_INCOMING
        Notification.CATEGORY_ALARM -> IslandIconKey.ALARM
        Notification.CATEGORY_NAVIGATION -> IslandIconKey.NAVIGATION
        Notification.CATEGORY_TRANSPORT -> IslandIconKey.MUSIC
        Notification.CATEGORY_PROGRESS -> IslandIconKey.DOWNLOAD
        Notification.CATEGORY_EVENT -> IslandIconKey.NOTIFICATION
        Notification.CATEGORY_SYSTEM, Notification.CATEGORY_SERVICE -> IslandIconKey.INFO
        Notification.CATEGORY_ERROR, Notification.CATEGORY_CRASH -> IslandIconKey.WARNING
        Notification.CATEGORY_REMINDER -> IslandIconKey.TIMER
        else -> IslandIconKey.NOTIFICATION
    }

    private fun actionsFor(info: NotificationInfo, groupSize: Int): List<IslandAction> {
        val actions = mutableListOf<IslandAction>()
        actions += IslandAction(
            id = "open",
            kind = IslandActionKind.OPEN_SOURCE_APP,
            iconKey = IslandIconKey.APP,
            labelKey = ActionLabelKey.OPEN,
        )
        // At most two source actions: the island is compact and never mimics the shade.
        info.actionLabels.take(2).forEachIndexed { index, label ->
            actions += IslandAction(
                id = "source-$index",
                kind = IslandActionKind.SOURCE_ACTION,
                iconKey = IslandIconKey.NOTIFICATION,
                label = Redaction.truncate(label, 18),
                sourceActionIndex = index,
            )
        }
        if (groupSize <= 1 && !info.isOngoing) {
            actions += IslandAction(
                id = "dismiss",
                kind = IslandActionKind.DISMISS,
                iconKey = IslandIconKey.WARNING,
                labelKey = ActionLabelKey.DISMISS,
            )
        }
        return actions
    }

    private fun personCount(extras: Bundle): Int {
        val people = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, Person::class.java)?.size ?: 0
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelableArrayList<Parcelable>(Notification.EXTRA_PEOPLE_LIST)?.size ?: 0
                }
            } else {
                0
            }
        }.getOrDefault(0)
        if (people > 0) return people

        return runCatching { extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.size ?: 0 }.getOrDefault(0)
    }

    private fun appLabel(packageName: String): String {
        if (packageName.isBlank()) return context.getString(R.string.app_name)
        return runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.recoverCatching {
            // Package visibility can be restricted; fall back to the parsed package name.
            packageName.substringAfterLast('.')
        }.getOrElse {
            logger.v(TAG, "app label unavailable for ${Redaction.safePackage(packageName)}")
            packageName.substringAfterLast('.')
        }
    }

    companion object {
        private const val TAG = "NotificationFactory"

        /** Convenience for callers that only have the framework object. */
        fun importanceOf(notification: Notification): NotificationImportance =
            NotificationImportance.from(notification.importance)
    }
}
