package dev.island.data.notifications

import dev.island.domain.model.NotificationInfo

/** A set of notifications that the island shows as one entry. */
data class NotificationGroup(
    val packageName: String,
    val appName: String,
    val items: List<NotificationInfo>,
    /** The notification whose content represents the group (the summary, or the newest). */
    val representative: NotificationInfo,
    val hasSystemSummary: Boolean,
) {
    val size: Int get() = items.size
    val isSingle: Boolean get() = items.size == 1
}

/**
 * Pure grouping rules.
 *
 * - Android's own group summaries win: if the app posted a summary notification we use it and
 *   list the children in the expanded card ("3 new messages" → Person A / B / C).
 * - Otherwise notifications sharing a `groupKey` (or, failing that, a package) collapse into one
 *   island entry as soon as there are two of them.
 * - Ongoing/foreground-service notifications never join a group: they are status, not messages.
 */
object NotificationGrouping {

    fun group(notifications: List<NotificationInfo>, groupingEnabled: Boolean): List<NotificationGroup> {
        if (notifications.isEmpty()) return emptyList()
        if (!groupingEnabled) {
            return notifications.map {
                NotificationGroup(
                    packageName = it.packageName,
                    appName = it.appName,
                    items = listOf(it),
                    representative = it,
                    hasSystemSummary = it.isGroupSummary,
                )
            }
        }

        val groups = LinkedHashMap<String, MutableList<NotificationInfo>>()
        notifications.forEach { notification ->
            val key = bucketKey(notification)
            groups.getOrPut(key) { mutableListOf() }.add(notification)
        }

        return groups.values.map { items ->
            val summary = items.firstOrNull { it.isGroupSummary }
            val ordered = items.sortedByDescending { it.postedAtMs }
            val representative = summary ?: ordered.first()
            val children = if (summary != null) ordered.filterNot { it.isGroupSummary } else ordered
            NotificationGroup(
                packageName = representative.packageName,
                appName = representative.appName,
                items = children.ifEmpty { ordered },
                representative = representative,
                hasSystemSummary = summary != null,
            )
        }
    }

    /** Group members for one notification key, or an empty list when it stands alone. */
    fun siblingsOf(key: String, all: List<NotificationInfo>, groupingEnabled: Boolean): List<NotificationInfo> {
        val target = all.firstOrNull { it.key == key } ?: return emptyList()
        if (!groupingEnabled) return emptyList()
        val bucket = bucketKey(target)
        val siblings = all.filter { bucketKey(it) == bucket }
        return if (siblings.size < 2) emptyList() else siblings.filterNot { it.key == key }
    }

    /** Stable bucket id; also used as the island coalesce key for grouped notifications. */
    fun bucketKey(notification: NotificationInfo): String {
        // Status notifications stay separate so a download never merges into a chat group.
        if (notification.isOngoing || notification.isForegroundService) return "ongoing:${notification.key}"
        val group = notification.groupKey?.takeIf { it.isNotBlank() }
        return if (group != null) "group:$group" else "pkg:${notification.packageName}"
    }
}
