package dev.island

import dev.island.domain.model.CallInfo
import dev.island.domain.model.CallState
import dev.island.domain.model.DeviceContext
import dev.island.domain.model.IslandAction
import dev.island.domain.model.IslandActionKind
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandEventMeta
import dev.island.domain.model.IslandEventType
import dev.island.domain.model.IslandIconKey
import dev.island.domain.model.IslandPriority
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.MediaInfo
import dev.island.domain.model.NotificationInfo
import dev.island.domain.model.PlaybackState
import dev.island.domain.repository.DeviceStateRepository
import dev.island.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Pure domain fixtures.
 *
 * Everything here is plain Kotlin — no Robolectric, no Android classes — because the domain layer
 * is deliberately Android-free. That is what makes these tests run on the JVM in milliseconds.
 */
object TestEvents {

    fun meta(
        id: String,
        type: IslandEventType,
        priority: IslandPriority = IslandPriority.MEDIUM,
        persistent: Boolean = false,
        title: String = "Title $id",
        subtitle: String? = "Subtitle $id",
        progress: Float? = null,
        expiresAt: Long? = null,
        coalesceKey: String = id,
        actions: List<IslandAction> = emptyList(),
    ) = IslandEventMeta(
        id = id,
        type = type,
        priority = priority,
        createdAt = 0L,
        expiresAt = expiresAt,
        title = title,
        subtitle = subtitle,
        iconKey = IslandIconKey.INFO,
        progress = progress,
        actions = actions,
        persistent = persistent,
        sourcePackage = "com.example.$id",
        sourceLabel = "Example $id",
        coalesceKey = coalesceKey,
    )

    fun notification(
        id: String = "n1",
        priority: IslandPriority = IslandPriority.MEDIUM,
        title: String? = "Ava Chen",
        text: String? = "Are we still on for 7?",
        packageName: String = "com.example.chat",
        appName: String = "Messages",
        visibilityPrivate: Boolean = false,
        groupKey: String? = null,
    ) = IslandEvent.Notification(
        meta = meta(id = id, type = IslandEventType.NOTIFICATION, priority = priority, title = title ?: appName),
        notification = NotificationInfo(
            key = "$packageName:$id",
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            visibilityPrivate = visibilityPrivate,
            groupKey = groupKey,
            actionCount = 2,
            actionLabels = listOf("Reply", "Mark read"),
        ),
    )

    fun media(
        id: String = "media",
        playing: Boolean = true,
        title: String = "Midnight City Lights",
        artist: String = "Neon Harbour",
        durationMs: Long? = 224_000L,
        positionMs: Long = 76_000L,
        sampledAtElapsedMs: Long = 0L,
    ) = IslandEvent.Media(
        meta = meta(
            id = id,
            type = IslandEventType.MEDIA,
            priority = IslandPriority.MEDIUM,
            persistent = true,
            title = title,
            subtitle = artist,
        ),
        media = MediaInfo(
            title = title,
            artist = artist,
            durationMs = durationMs,
            positionMs = positionMs,
            positionSampledAtElapsedMs = sampledAtElapsedMs,
            state = if (playing) PlaybackState.PLAYING else PlaybackState.PAUSED,
            canPlayPause = true,
            canSkipNext = true,
            canSkipPrevious = true,
        ),
    )

    fun call(
        id: String = "call",
        state: CallState = CallState.INCOMING,
        handle: String? = "+15550134",
    ) = IslandEvent.Call(
        meta = meta(
            id = id,
            type = IslandEventType.CALL,
            priority = IslandPriority.CRITICAL,
            persistent = true,
            title = handle ?: "Unknown",
        ),
        call = CallInfo(state = state, handle = handle, canAnswer = true, canEnd = true, canMute = true),
    )

    fun custom(id: String = "custom", progress: Float? = 0.4f) = IslandEvent.Custom(
        meta = meta(id = id, type = IslandEventType.CUSTOM, progress = progress),
    )
}

/** Settings repository double: emits the current value and records every transform. */
class FakeSettingsRepository(initial: IslandSettings = IslandSettings(islandEnabled = true)) :
    SettingsRepository {

    private val state = MutableStateFlow(initial)
    val updates = mutableListOf<(IslandSettings) -> IslandSettings>()

    override val settings: Flow<IslandSettings> = state

    override suspend fun current(): IslandSettings = state.value

    override suspend fun update(transform: (IslandSettings) -> IslandSettings) {
        updates += transform
        state.value = transform(state.value)
    }

    override suspend fun reset(): IslandSettings {
        state.value = IslandSettings()
        return state.value
    }

    fun set(settings: IslandSettings) {
        state.value = settings
    }
}

/** Device state double: controllable screen/DND/landscape flags. */
class FakeDeviceStateRepository(initial: DeviceContext = DeviceContext()) : DeviceStateRepository {

    private val state = MutableStateFlow(initial)
    var startCount = 0
        private set
    var stopCount = 0
        private set

    override val deviceContext: Flow<DeviceContext> = state

    override fun start() {
        startCount++
    }

    override fun stop() {
        stopCount++
    }

    fun set(context: DeviceContext) {
        state.value = context
    }
}
