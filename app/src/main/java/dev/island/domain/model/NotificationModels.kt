package dev.island.domain.model

/**
 * Maps onto `NotificationManager.IMPORTANCE_*`. Island never shows a notification the user's
 * own channel settings silenced, and importance drives whether the island expands or merely
 * shows a compact pill.
 */
enum class NotificationImportance(val value: Int) {
    NONE(0),
    MIN(1),
    LOW(2),
    DEFAULT(3),
    HIGH(4),
    /** Reserved for full-screen-intent / call style notifications. */
    URGENT(5),
    ;

    companion object {
        fun from(value: Int): NotificationImportance = entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

/** What the island is allowed to do with a notification from one specific app. */
enum class AppNotificationMode {
    /** Follow the global privacy setting. */
    ALWAYS,
    NEVER,
    IMPORTANT_ONLY,
    ICON_ONLY,
    FULL_PREVIEW,
}

/** Per-app rule, stored locally in DataStore. */
data class AppNotificationRule(
    val packageName: String,
    val appLabel: String? = null,
    val mode: AppNotificationMode = AppNotificationMode.ALWAYS,
)

/**
 * A parsed notification. Content fields are only populated when the user's privacy settings
 * allow it — see [dev.island.domain.engine.PrivacyMasker].
 */
data class NotificationInfo(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val bigText: String? = null,
    val category: String? = null,
    val importance: NotificationImportance = NotificationImportance.DEFAULT,
    val postedAtMs: Long = 0L,
    val isOngoing: Boolean = false,
    val isGroupSummary: Boolean = false,
    val groupKey: String? = null,
    val groupSize: Int = 1,
    val hasProgress: Boolean = false,
    val progressMax: Int = 0,
    val progressCurrent: Int = 0,
    val progressIndeterminate: Boolean = false,
    val actionCount: Int = 0,
    val actionLabels: List<String> = emptyList(),
    val isMediaStyle: Boolean = false,
    val isCallStyle: Boolean = false,
    val visibilityPrivate: Boolean = false,
    val isForegroundService: Boolean = false,
    val personCount: Int = 0,
) {
    /** Progress in 0f..1f when the notification actually publishes determinate progress. */
    val progressFraction: Float?
        get() {
            if (!hasProgress || progressIndeterminate || progressMax <= 0) return null
            return (progressCurrent.toFloat() / progressMax.toFloat()).coerceIn(0f, 1f)
        }

    val isDeterminantProgress: Boolean get() = progressFraction != null

    /** One-line body used by the collapsed pill. */
    val previewText: String?
        get() = text ?: subText ?: bigText
}
