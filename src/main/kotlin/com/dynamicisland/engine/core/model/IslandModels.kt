package com.dynamicisland.engine.core.model

import android.app.PendingIntent
import android.graphics.Bitmap
import android.net.Uri

/**
 * Priority rank replicating iOS Jindo system rules.
 * Lower integer value = higher priority.
 *
 * Rule Hierarchy:
 * Emergency/Call (100) > Live Timer/Stopwatch (200) > Navigation (300) > Media Playback (400) > System/Transient Alert (500)
 */
enum class PriorityRank(val order: Int) {
    EMERGENCY_OR_CALL(100),
    LIVE_TIMER_STOPWATCH(200),
    NAVIGATION_TURN_BY_TURN(300),
    MEDIA_PLAYBACK(400),
    TRANSIENT_SYSTEM_ALERT(500),
    LOW_PRIORITY_NOTIFICATION(600)
}

/**
 * Kind of system activity running in the Dynamic Island.
 */
enum class ActivityKind {
    EMERGENCY_CALL,
    RINGING_CALL,
    LIVE_CALL,
    LIVE_TIMER,
    LIVE_STOPWATCH,
    NAVIGATION,
    MEDIA_SESSION,
    BATTERY_CHARGING,
    RINGER_DND_STATUS,
    ACCESSORY_BATTERY,
    HOTSPOT_STATUS,
    TRANSIENT_NOTIFICATION
}

/**
 * Universal Action executable from the Dynamic Island.
 */
data class IslandAction(
    val id: String,
    val title: String,
    val iconUri: Uri? = null,
    val iconResId: Int? = null,
    val isDestructive: Boolean = false,
    val pendingIntent: PendingIntent? = null,
    val actionCallback: (() -> Unit)? = null,
    val semantics: ActionSemantics = ActionSemantics.UNKNOWN
)

/**
 * Monochrome vector symbols (SF Symbols equivalents) used by the island slots.
 *
 * These replace the emoji glyphs the engine previously embedded in text slots:
 * emoji render with the system colour-emoji font, which broke the monochrome
 * iOS look (a red musical note, a grey pause badge, a literal "X" after a mic).
 */
enum class IslandSymbol {
    MUSIC_NOTE,
    BELL,
    BELL_SLASH,
    BOLT,
    HEADSET,
    WIFI,
    PHONE,
    PHONE_END,
    TIMER,
    NAVIGATION,
    MIC,
    MIC_OFF,
    SPEAKER,
    AIRPLAY,
    BATTERY,
    CLOSE,
    PLAY,
    PAUSE
}

/**
 * Snapshot of the currently tracked media session, published for the overlay UI.
 *
 * The island previously hard-coded `isPlaying = true`, `positionMs = 65_000` and
 * `durationMs = 210_000`, so the expanded card always rendered "1:05 / -2:25"
 * with a pause glyph even when playback was stopped.
 */
data class MediaPlaybackSnapshot(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val lastUpdateElapsedRealtime: Long = 0L,
    val supportedActions: Long = 0L
) {
    val canPlayPause: Boolean get() = supportedActions and (android.media.session.PlaybackState.ACTION_PLAY_PAUSE or if (isPlaying) android.media.session.PlaybackState.ACTION_PAUSE else android.media.session.PlaybackState.ACTION_PLAY) != 0L
    val canNext: Boolean get() = supportedActions and android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT != 0L
    val canPrevious: Boolean get() = supportedActions and android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L
    val canSeek: Boolean get() = durationMs > 0 && supportedActions and android.media.session.PlaybackState.ACTION_SEEK_TO != 0L
    /** Position extrapolated to now, using the platform playback speed. */
    fun positionAt(nowElapsedRealtime: Long): Long {
        if (!isPlaying || playbackSpeed <= 0f) return positionMs
        val delta = (nowElapsedRealtime - lastUpdateElapsedRealtime).coerceAtLeast(0L)
        return (positionMs + (delta * playbackSpeed).toLong())
            .coerceIn(0L, if (durationMs > 0L) durationMs else Long.MAX_VALUE)
    }
}

/**
 * Slot content for compact and minimal presentations.
 */
sealed interface IslandSlotContent {
    /** Monochrome vector glyph slot (preferred over [Text] for all iconography). */
    data class Symbol(
        val symbol: IslandSymbol,
        val tintHex: String? = null
    ) : IslandSlotContent

    data class Text(
        val text: String,
        val colorHex: String? = null,
        val isMonospace: Boolean = false
    ) : IslandSlotContent

    data class Icon(
        val iconResId: Int? = null,
        val iconBitmap: Bitmap? = null,
        val iconUri: Uri? = null,
        val tintHex: String? = null
    ) : IslandSlotContent

    data class Progress(
        val current: Int,
        val max: Int,
        val isIndeterminate: Boolean = false,
        val tintHex: String? = null
    ) : IslandSlotContent

    /**
     * Ticking clock slot.
     *
     * [baseTimeMillis] uses the wall clock (`System.currentTimeMillis()`), matching
     * `Notification.EXTRA_CHRONOMETER_BASE`. For a countdown this is the *target* instant.
     */
    data class Chronometer(
        val baseTimeMillis: Long,
        val isCountDown: Boolean = false,
        val isRunning: Boolean = true,
        val colorHex: String? = null
    ) : IslandSlotContent

    data class AudioWaveform(
        val amplitudes: List<Float>, // 0.0f to 1.0f
        val tintHex: String = "#1DB954"
    ) : IslandSlotContent

    data class Composite(
        val leadingIcon: Icon? = null,
        val text: Text? = null
    ) : IslandSlotContent
}

/**
 * Rich expanded layout regions modeled after iOS DynamicIslandExpandedRegion:
 * - Leading: Avatar, app icon, or small status
 * - Trailing: Progress, timer, or status text
 * - Center: Title, route, or prominent info
 * - Bottom: Controls, action buttons, progress bar, slider
 */
data class ExpandedRegionData(
    val leading: IslandSlotContent,
    val trailing: IslandSlotContent,
    val center: IslandSlotContent? = null,
    val bottom: IslandSlotContent? = null,
    val actions: List<IslandAction> = emptyList(),
    val progressFraction: Float? = null,
    val progressLabel: String? = null,
    val customPayload: Map<String, Any> = emptyMap()
)

/**
 * Real call audio state reported by Telecom / AudioManager (never fabricated).
 */
data class CallUiState(
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isOnHold: Boolean = false,
    /**
     * Whether the call itself can be accepted or ended from the island.
     *
     * Only true while this app is the default dialer (`InCallService` bound).
     * Mute and speaker are always available through `AudioManager`, but answer
     * and hang-up have no fallback API, so the card must not offer them when
     * they cannot work.
     */
    val isControllable: Boolean = false
)

/**
 * An active ongoing Island Activity registered with the arbiter.
 */
data class IslandActivity(
    val id: String,
    val ownerPackage: String,
    val title: String,
    val subtitle: String? = null,
    val kind: ActivityKind,
    val rank: PriorityRank,
    val startedAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val compactLeading: IslandSlotContent,
    val compactTrailing: IslandSlotContent,
    val minimalContent: IslandSlotContent,
    val expandedData: ExpandedRegionData,
    val deepLinkIntent: PendingIntent? = null,
    val actions: List<IslandAction> = emptyList(),
    val callState: CallUiState? = null,
    val timeoutMillis: Long? = null // Optional auto-expiration
) : Comparable<IslandActivity> {

    /**
     * Total ordering comparator replicating iOS Jindo priority rules:
     * 1. Priority rank (smaller rank = higher priority)
     * 2. StartedAt timestamp (earlier activity holds lock unless user interacts)
     * 3. Deterministic ID comparison to guarantee no tie
     */
    override fun compareTo(other: IslandActivity): Int {
        val rankCompare = this.rank.order.compareTo(other.rank.order)
        if (rankCompare != 0) return rankCompare

        val startCompare = this.startedAt.compareTo(other.startedAt)
        if (startCompare != 0) return startCompare

        return this.id.compareTo(other.id)
    }
}

/**
 * Transient Alert data for temporary popouts (charging connected, silent switch, AirPod battery, etc.)
 */
data class TransientAlert(
    val id: String,
    val title: String,
    val message: String? = null,
    val leadingSlot: IslandSlotContent,
    val trailingSlot: IslandSlotContent? = null,
    val expandedData: ExpandedRegionData? = null,
    val durationMillis: Long = 3200L,
    val rank: PriorityRank = PriorityRank.TRANSIENT_SYSTEM_ALERT,
    val deepLinkIntent: PendingIntent? = null,
    val sound: Boolean = false,
    val haptic: Boolean = true
)

/**
 * One notification held in the island's inbox.
 *
 * Distinct from [TransientAlert], which is a *momentary* heads-up that shows
 * itself and expires. An inbox entry is the opposite: it arrives, stays, and is
 * only removed when the user clears it. Both exist because they answer different
 * questions — "what just happened?" versus "what is waiting for me?".
 *
 * Deliberately holds no `PendingIntent` and no Android type. The inbox is
 * ordered, compared and unit-tested in the pure core, and the platform handle
 * that lets a tap open the notification is resolved by the adapter layer from
 * [key] when it is actually needed.
 */
data class IslandNotification(
    /** Stable identity, matching the platform's notification key. */
    val key: String,
    val packageName: String,
    val title: String,
    val body: String? = null,
    /** Wall-clock arrival time, used only to order newest-first. */
    val postedAtMillis: Long,
    /** True when the source app marked this as an ongoing/live activity. */
    val isOngoing: Boolean = false,
    /** Display name of the source app, resolved by the adapter when available. */
    val appLabel: String? = null,
    val groupKey: String? = null,
    val importance: Int = 3
)

/**
 * Presentation states of the Dynamic Island.
 * Closed -> Compact -> Minimal -> Expanded -> AlertPeek -> NotificationInbox
 */
sealed interface IslandState {
    /**
     * Island is collapsed into the hardware camera cutout / pill.
     */
    object Closed : IslandState

    /**
     * Single active activity taking the full island pill:
     * Compact Leading view + Compact Trailing view.
     */
    data class Compact(
        val activity: IslandActivity
    ) : IslandState

    /**
     * Multi-island mode when 2 or more activities are active simultaneously:
     * - Primary activity: displayed in the attached minimal pill around the cutout.
     * - Secondary activity: displayed in the detached minimal bubble beside the cutout.
     */
    data class Minimal(
        val primary: IslandActivity,
        val secondary: IslandActivity,
        val totalActiveActivitiesCount: Int
    ) : IslandState

    /**
     * Expanded presentation card showing rich details and interactive controls.
     */
    data class Expanded(
        val activity: IslandActivity,
        val regionData: ExpandedRegionData
    ) : IslandState

    /**
     * Transient Alert Peek (temporary alert expanding from island, auto-dismissing after duration).
     */
    data class AlertPeek(
        val alert: TransientAlert,
        val expiresAtMillis: Long,
        val underlyingState: IslandState
    ) : IslandState

    /**
     * The accumulated notification inbox, shown when the island is opened.
     *
     * Newest first, because the thing that just arrived is almost always the
     * thing being looked for. The list is the whole state rather than a cursor:
     * the island shows the most recent few and the count of the rest, so it
     * never needs to scroll.
     */
    data class NotificationInbox(
        val notifications: List<IslandNotification>,
        /** True when the island is only showing a "N new" badge, not the list. */
        val collapsed: Boolean = false
    ) : IslandState {
        /** How many notifications are waiting. */
        val count: Int get() = notifications.size

        /** The most recent entry, which is the one a compact badge names. */
        val newest: IslandNotification? get() = notifications.firstOrNull()
    }
}
