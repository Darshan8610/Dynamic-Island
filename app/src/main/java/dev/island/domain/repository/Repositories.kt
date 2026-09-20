package dev.island.domain.repository

import dev.island.domain.model.DeviceContext
import dev.island.domain.model.HistoryEntry
import dev.island.domain.model.IslandAction
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.MediaInfo
import dev.island.domain.model.PermissionStatus
import dev.island.domain.model.StopwatchInfo
import dev.island.domain.model.TimerInfo
import kotlinx.coroutines.flow.Flow

/** Single writer of user configuration. All feature screens mutate through [update]. */
interface SettingsRepository {
    val settings: Flow<IslandSettings>
    suspend fun current(): IslandSettings
    suspend fun update(transform: (IslandSettings) -> IslandSettings)
    suspend fun reset(): IslandSettings
}

/** Live device state (screen, DND, orientation, battery). Callback driven — no polling. */
interface DeviceStateRepository {
    val deviceContext: Flow<DeviceContext>
    fun start()
    fun stop()
}

/** Media session bridge. Implementations must never steal focus from the system UI. */
interface MediaRepository {
    val media: Flow<MediaInfo?>
    fun start()
    fun stop()
    suspend fun play()
    suspend fun pause()
    suspend fun togglePlayPause()
    suspend fun skipNext()
    suspend fun skipPrevious()
    suspend fun seekTo(positionMs: Long)
}

/** Countdown timers + stopwatch, monotonic and process-death resilient. */
interface TimerRepository {
    val timers: Flow<List<TimerInfo>>
    val stopwatch: Flow<StopwatchInfo?>
    fun start()
    suspend fun createTimer(durationMs: Long, label: String?): String
    suspend fun toggleTimer(timerId: String)
    suspend fun pauseTimer(timerId: String)
    suspend fun resumeTimer(timerId: String)
    suspend fun stopTimer(timerId: String)
    suspend fun toggleStopwatch()
    suspend fun resetStopwatch()
    suspend fun lapStopwatch()
}

/** Opt-in, metadata-only local history. */
interface EventHistoryRepository {
    val entries: Flow<List<HistoryEntry>>
    suspend fun record(entry: HistoryEntry)
    suspend fun clear()
    suspend fun exportAsText(): String
}

/** Runtime permission/capability snapshot used by diagnostics and the engine gate. */
interface PermissionRepository {
    val statuses: Flow<List<PermissionStatus>>
    fun refresh()
}

/**
 * Extension point for future turn-by-turn integrations (Google Maps and others) through
 * legitimate Android mechanisms. Island never scrapes the screen or accessibility tree.
 */
interface NavigationProvider {
    val id: String
    val isAvailable: Boolean
    val events: Flow<IslandEvent.Navigation>
}

/** Executes the action behind an island button (transport controls, timer ops, open app…). */
interface IslandActionHandler {
    suspend fun handle(action: IslandAction, event: IslandEvent)
}
