package dev.island.domain.model

/** Playback state as reported by a [android.media.session.MediaController]. */
enum class PlaybackState {
    PLAYING,
    PAUSED,
    BUFFERING,
    STOPPED,
    UNKNOWN,
    ;

    val isActive: Boolean get() = this == PLAYING || this == BUFFERING
}

/**
 * Media snapshot. Bitmaps never live in the domain model: [artworkToken] is a key into
 * [dev.island.data.media.ArtworkCache], which keeps the model unit-testable on the JVM and
 * avoids leaking large objects through the event queue.
 */
data class MediaInfo(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val positionMs: Long = 0L,
    /** [android.os.SystemClock.elapsedRealtime] when [positionMs] was sampled. */
    val positionSampledAtElapsedMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val state: PlaybackState = PlaybackState.UNKNOWN,
    val artworkToken: String? = null,
    val sourcePackage: String? = null,
    val sourceLabel: String? = null,
    val canPlayPause: Boolean = false,
    val canSkipNext: Boolean = false,
    val canSkipPrevious: Boolean = false,
    val canSeek: Boolean = false,
) {
    /** Estimated position now, derived from the monotonic sample — no polling required. */
    fun positionAt(elapsedNowMs: Long): Long {
        if (state != PlaybackState.PLAYING) return positionMs
        val delta = (elapsedNowMs - positionSampledAtElapsedMs).coerceAtLeast(0L)
        val projected = positionMs + (delta * playbackSpeed).toLong()
        val duration = durationMs
        return if (duration != null && duration > 0) projected.coerceIn(0L, duration) else projected
    }

    /** 0f..1f, or `null` while the duration is unknown (live streams). */
    fun fractionAt(elapsedNowMs: Long): Float? {
        val duration = durationMs ?: return null
        if (duration <= 0L) return null
        return (positionAt(elapsedNowMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }
}
