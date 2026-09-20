package dev.island.domain.engine

import dev.island.domain.model.ActionLabelKey
import dev.island.domain.model.IslandAction
import dev.island.domain.model.IslandActionKind
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandIconKey
import dev.island.domain.model.NotificationInfo
import dev.island.domain.model.PrivacyLevel
import dev.island.domain.model.PrivacyMode
import dev.island.domain.model.IslandSettings

/**
 * Applies the user's privacy mode to an event *before* it reaches the renderer, so sensitive
 * text never exists in the overlay's composition tree at all.
 *
 * Modes:
 * - FULL              → title + content
 * - PRIVATE           → app name only, the UI shows a generic "New message" style line
 * - ICON_ONLY         → app icon (plus count), no text
 * - SENSITIVE_HIDDEN  → FULL, except for notifications the source flagged private/secret
 *
 * On the lock screen [IslandSettings.privacy] decides independently (default: ICON_ONLY), and
 * Island never renders an expanded card there unless the user opted in.
 */
object PrivacyMasker {

    fun effectiveMode(settings: IslandSettings, screenLocked: Boolean): PrivacyMode =
        if (screenLocked) settings.privacy.lockScreenMode else settings.notifications.privacyMode

    fun mask(event: IslandEvent, settings: IslandSettings, screenLocked: Boolean): IslandEvent {
        val mode = effectiveMode(settings, screenLocked)
        return when (event) {
            is IslandEvent.Notification -> maskNotification(event, settings, mode)
            is IslandEvent.Call -> maskCall(event, settings, mode)
            else -> event
        }
    }

    private fun maskNotification(
        event: IslandEvent.Notification,
        settings: IslandSettings,
        mode: PrivacyMode,
    ): IslandEvent {
        val source = event.notification
        val sourceIsPrivate = source.visibilityPrivate ||
            (settings.notifications.hideSensitiveContent && source.visibilityPrivate)

        val effective = when {
            mode == PrivacyMode.ICON_ONLY -> PrivacyMode.ICON_ONLY
            mode == PrivacyMode.PRIVATE -> PrivacyMode.PRIVATE
            mode == PrivacyMode.SENSITIVE_HIDDEN && sourceIsPrivate -> PrivacyMode.PRIVATE
            mode == PrivacyMode.FULL && sourceIsPrivate && settings.notifications.hideSensitiveContent -> PrivacyMode.PRIVATE
            else -> PrivacyMode.FULL
        }

        val maskedInfo = when (effective) {
            PrivacyMode.FULL -> source

            PrivacyMode.PRIVATE -> source.copy(
                title = null,
                text = null,
                bigText = null,
                subText = null,
                actionLabels = emptyList(),
            )

            PrivacyMode.ICON_ONLY, PrivacyMode.SENSITIVE_HIDDEN -> source.copy(
                title = null,
                text = null,
                bigText = null,
                subText = null,
                actionLabels = emptyList(),
                actionCount = 0,
            )
        }

        val maskedGroup = if (effective == PrivacyMode.FULL) {
            event.grouped
        } else {
            // Keep the count, drop the identities.
            event.grouped.map { it.copy(title = null, text = null, bigText = null, subText = null) }
        }

        val level = when (effective) {
            PrivacyMode.FULL -> PrivacyLevel.PUBLIC
            PrivacyMode.PRIVATE, PrivacyMode.SENSITIVE_HIDDEN -> PrivacyLevel.PRIVATE
            PrivacyMode.ICON_ONLY -> PrivacyLevel.SECRET
        }

        val title = when (effective) {
            PrivacyMode.FULL -> source.title ?: source.appName
            PrivacyMode.PRIVATE -> source.appName
            PrivacyMode.ICON_ONLY, PrivacyMode.SENSITIVE_HIDDEN -> source.appName
        }

        val subtitle = when (effective) {
            PrivacyMode.FULL -> source.previewText
            else -> null
        }

        val actions = when (effective) {
            PrivacyMode.FULL -> event.meta.actions
            // Never expose source notification actions (they can contain message text / replies).
            else -> event.meta.actions.filter {
                it.kind == IslandActionKind.DISMISS || it.kind == IslandActionKind.OPEN_SOURCE_APP
            }
        }

        return event.copy(
            meta = event.meta.copy(
                title = title,
                subtitle = subtitle,
                privacyLevel = level,
                actions = actions,
                iconKey = if (effective == PrivacyMode.ICON_ONLY) IslandIconKey.APP else event.meta.iconKey,
                // Progress is not content; keep it so downloads still make sense in private mode.
                progress = if (effective == PrivacyMode.ICON_ONLY) null else event.meta.progress,
            ),
            notification = maskedInfo,
            grouped = maskedGroup,
        )
    }

    /**
     * Call events are produced with a generic title ("Incoming call") and the handle in the
     * subtitle, so masking only has to drop the subtitle and the raw handle.
     */
    private fun maskCall(event: IslandEvent.Call, settings: IslandSettings, mode: PrivacyMode): IslandEvent {
        val hideHandle = !settings.calls.showHandle || mode == PrivacyMode.ICON_ONLY || mode == PrivacyMode.PRIVATE
        if (!hideHandle) return event
        return event.copy(
            meta = event.meta.copy(
                subtitle = null,
                privacyLevel = PrivacyLevel.PRIVATE,
                actions = event.meta.actions.filterNot { it.kind == IslandActionKind.SOURCE_ACTION },
            ),
            call = event.call.copy(handle = null),
        )
    }

    /** Convenience for producers: an "open app" + "dismiss" pair used when content is hidden. */
    fun minimalActions(openLabel: ActionLabelKey = ActionLabelKey.OPEN): List<IslandAction> = listOf(
        IslandAction(id = "open", kind = IslandActionKind.OPEN_SOURCE_APP, iconKey = IslandIconKey.APP, labelKey = openLabel),
        IslandAction(id = "dismiss", kind = IslandActionKind.DISMISS, iconKey = IslandIconKey.WARNING, labelKey = ActionLabelKey.DISMISS),
    )
}
