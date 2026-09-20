package dev.island.data.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState as AndroidPlaybackState
import android.os.Handler
import android.os.Looper
import dev.island.core.logging.IslandLogger
import dev.island.core.logging.Redaction
import dev.island.domain.model.MediaInfo
import dev.island.domain.model.PlaybackState
import dev.island.domain.repository.MediaRepository
import dev.island.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Media integration through the platform MediaSession APIs — no app-specific hacks, no
 * hard-coded package names. Any app that publishes a [MediaController] shows up here.
 *
 * Requirements and limits, documented rather than papered over:
 * - `MediaSessionManager.getActiveSessions` needs the notification-listener component to be
 *   enabled by the user. Without it a [SecurityException] is caught and the media island simply
 *   stays disabled (the rest of Island keeps working).
 * - Island issues transport commands through [MediaController.getTransportControls]; it never
 *   requests audio focus, never becomes a media session itself and never intercepts keys.
 * - Position is derived from `PlaybackState.position` + `lastPositionUpdateTime` (monotonic), so
 *   progress advances without polling the session.
 */
class MediaSessionRepositoryImpl(
    private val context: Context,
    private val scope: CoroutineScope,
    private val logger: IslandLogger,
    private val artworkCache: ArtworkCache,
    private val settingsRepository: SettingsRepository,
) : MediaRepository {

    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val listenerComponent =
        ComponentName(context, dev.island.core.permissions.AndroidPermissionRepository.NOTIFICATION_LISTENER_CLASS)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _media = MutableStateFlow<MediaInfo?>(null)
    private val mediaState: StateFlow<MediaInfo?> = _media

    override val media = mediaState

    private var activeController: MediaController? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var started = false

    /** Mirrors [dev.island.domain.model.MediaSettings.showAlbumArt] without blocking on a flow. */
    @Volatile
    private var showAlbumArt: Boolean = true
    private var settingsJob: kotlinx.coroutines.Job? = null

    /** True when the platform refused session access (notification listener disabled). */
    @Volatile
    var accessDenied: Boolean = false
        private set

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish("metadata")
        override fun onPlaybackStateChanged(state: AndroidPlaybackState?) = publish("playbackState")
        override fun onSessionDestroyed() {
            logger.d(TAG, "session destroyed")
            refreshSessions("session destroyed")
        }

        override fun onSessionReleased() = refreshSessions("session released")
        override fun onAudioInfoChanged(info: MediaController.PlaybackInfo?) = publish("audioInfo")
    }

    override fun start() {
        if (started) return
        started = true
        settingsJob?.cancel()
        settingsJob = scope.launch {
            settingsRepository.settings.collect { showAlbumArt = it.media.showAlbumArt }
        }
        refreshSessions("start")
    }

    override fun stop() {
        if (!started) return
        started = false
        settingsJob?.cancel()
        settingsJob = null
        detachController()
        sessionsListener?.let { listener ->
            runCatching { sessionManager?.removeOnActiveSessionsChangedListener(listener) }
        }
        sessionsListener = null
        _media.value = null
        logger.d(TAG, "media monitor stopped")
    }

    /** Re-reads the session list; called on start, on session changes and after permission grants. */
    fun refreshSessions(reason: String) {
        val manager = sessionManager
        if (manager == null) {
            logger.w(TAG, "MediaSessionManager unavailable")
            return
        }
        if (sessionsListener == null) {
            val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                onSessionsChanged(controllers)
            }
            runCatching { manager.addOnActiveSessionsChangedListener(listener, listenerComponent, mainHandler) }
                .onSuccess { sessionsListener = listener }
                .onFailure { onAccessDenied(it, reason) }
        }

        val sessions = runCatching { manager.getActiveSessions(listenerComponent) }
            .onFailure { onAccessDenied(it, reason) }
            .getOrDefault(emptyList())
        onSessionsChanged(sessions)
    }

    private fun onAccessDenied(cause: Throwable, reason: String) {
        accessDenied = true
        _media.value = null
        logger.w(TAG, "media session access denied ($reason): ${cause.javaClass.simpleName}")
    }

    private fun onSessionsChanged(controllers: List<MediaController>?) {
        accessDenied = false
        val list = controllers.orEmpty()
        val selected = selectController(list)
        // Package identity is the stable key here: getActiveSessions() hands back fresh
        // MediaController objects every call, so comparing instances would churn callbacks.
        if (selected?.packageName != activeController?.packageName) {
            detachController()
            activeController = selected
            selected?.registerCallback(controllerCallback, mainHandler)
            logger.d(TAG, "active session -> ${Redaction.safePackage(selected?.packageName)}")
        }
        publish(if (list.isEmpty()) "no sessions" else "${list.size} sessions")
    }

    /** Prefer whatever is actually playing; otherwise the most recently updated session. */
    private fun selectController(sessions: List<MediaController>): MediaController? {
        if (sessions.isEmpty()) return null
        val playing = sessions.filter { it.playbackState?.state == AndroidPlaybackState.STATE_PLAYING }
        if (playing.isNotEmpty()) {
            return playing.maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L }
        }
        val withState = sessions.filter { it.playbackState != null }
        if (withState.isNotEmpty()) {
            return withState.maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L }
        }
        return sessions.firstOrNull()
    }

    private fun publish(reason: String) {
        val controller = activeController
        if (controller == null) {
            if (_media.value != null) _media.value = null
            return
        }
        val info = mapController(controller)
        if (info == null) {
            if (_media.value != null) _media.value = null
            return
        }
        if (_media.value != info) {
            _media.value = info
            logger.v(TAG, "media updated ($reason) state=${info.state} pkg=${Redaction.safePackage(info.sourcePackage)}")
        }
    }

    private fun mapController(controller: MediaController): MediaInfo? {
        val metadata = runCatching { controller.metadata }.getOrNull()
        val state = runCatching { controller.playbackState }.getOrNull()
        val description = runCatching { metadata?.description }.getOrNull()

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: description?.title?.toString()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: description?.subtitle?.toString()
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?: description?.description?.toString()

        // A session with no metadata and no state carries nothing worth showing.
        if (title.isNullOrBlank() && state == null) return null
        if (title.isNullOrBlank() && artist.isNullOrBlank() && state == null) return null

        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L }
        val position = state?.position?.coerceAtLeast(0L) ?: 0L
        val sampledAt = state?.lastPositionUpdateTime ?: android.os.SystemClock.elapsedRealtime()
        val speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f

        val actions = state?.actions ?: 0L
        val artworkToken = if (showAlbumArt) artworkCache.tokenFor(controller, metadata) else null
        // Warm the cache in the background so the first frame already has art.
        if (artworkToken != null && artworkCache.peek(artworkToken) == null) {
            scope.launch(Dispatchers.Default) {
                artworkCache.artworkFor(artworkToken) { ArtworkReader.from(metadata) }
            }
        }

        return MediaInfo(
            title = title?.takeIf { it.isNotBlank() } ?: UNKNOWN_TITLE,
            artist = artist?.takeIf { it.isNotBlank() },
            album = album?.takeIf { it.isNotBlank() },
            durationMs = duration,
            positionMs = position,
            positionSampledAtElapsedMs = sampledAt,
            playbackSpeed = speed,
            state = mapState(state?.state),
            artworkToken = artworkToken,
            sourcePackage = controller.packageName,
            sourceLabel = appLabel(controller.packageName),
            canPlayPause = actions.hasAll(ACTION_PLAY_PAUSE) ||
                (actions.hasAll(ACTION_PLAY) && actions.hasAll(ACTION_PAUSE)),
            canSkipNext = actions.hasAll(ACTION_SKIP_NEXT),
            canSkipPrevious = actions.hasAll(ACTION_SKIP_PREVIOUS),
            canSeek = actions.hasAll(ACTION_SEEK),
        )
    }

    private fun mapState(state: Int?): PlaybackState = when (state) {
        AndroidPlaybackState.STATE_PLAYING -> PlaybackState.PLAYING
        AndroidPlaybackState.STATE_PAUSED -> PlaybackState.PAUSED
        AndroidPlaybackState.STATE_BUFFERING -> PlaybackState.BUFFERING
        AndroidPlaybackState.STATE_STOPPED, AndroidPlaybackState.STATE_NONE -> PlaybackState.STOPPED
        else -> PlaybackState.UNKNOWN
    }

    private fun Long.hasAll(mask: Long): Boolean = (this and mask) == mask

    private fun appLabel(packageName: String?): String? = packageName?.let { pkg ->
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull()
    }

    private fun detachController() {
        runCatching { activeController?.unregisterCallback(controllerCallback) }
        activeController = null
    }

    /** Bitmap for the current artwork token, decoded at most once per track. */
    fun artworkFor(token: String?): android.graphics.Bitmap? {
        if (token == null) return null
        val controller = activeController ?: return artworkCache.peek(token)
        return artworkCache.artworkFor(token) {
            ArtworkReader.from(runCatching { controller.metadata }.getOrNull())
        }
    }

    // region transport controls

    override suspend fun play() = withTransport { it.play() }

    override suspend fun pause() = withTransport { it.pause() }

    override suspend fun togglePlayPause() {
        val controller = activeController ?: return
        val isPlaying = controller.playbackState?.state == AndroidPlaybackState.STATE_PLAYING
        withTransport { if (isPlaying) it.pause() else it.play() }
    }

    override suspend fun skipNext() = withTransport { it.skipToNext() }

    override suspend fun skipPrevious() = withTransport { it.skipToPrevious() }

    override suspend fun seekTo(positionMs: Long) = withTransport { it.seekTo(positionMs) }

    private suspend fun withTransport(block: (MediaController.TransportControls) -> Unit) {
        val controller = activeController ?: run {
            logger.d(TAG, "transport command ignored: no active session")
            return
        }
        withContext(Dispatchers.Main) {
            runCatching { block(controller.transportControls) }
                .onFailure { logger.w(TAG, "transport command failed", it) }
        }
        // Reflect the command immediately; the callback would arrive a few frames later.
        publish("transport command")
    }

    // endregion

    companion object {
        private const val TAG = "MediaRepo"
        private const val UNKNOWN_TITLE = "Now playing"
        private const val ACTION_PLAY_PAUSE =
            AndroidPlaybackState.ACTION_PLAY_PAUSE
        private const val ACTION_PLAY = AndroidPlaybackState.ACTION_PLAY
        private const val ACTION_PAUSE = AndroidPlaybackState.ACTION_PAUSE
        private const val ACTION_SKIP_NEXT = AndroidPlaybackState.ACTION_SKIP_TO_NEXT
        private const val ACTION_SKIP_PREVIOUS = AndroidPlaybackState.ACTION_SKIP_TO_PREVIOUS
        private const val ACTION_SEEK = AndroidPlaybackState.ACTION_SEEK_TO
    }
}
