package dev.island.domain.model

/**
 * Live device context the engine needs to gate events. Published by
 * [dev.island.data.device.DeviceStateRepositoryImpl]; consumed as a flow so nothing polls.
 */
data class DeviceContext(
    val screenOn: Boolean = true,
    val screenLocked: Boolean = false,
    val doNotDisturb: Boolean = false,
    val landscape: Boolean = false,
    val batteryPercent: Int = -1,
    val isCharging: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val isInteractiveDisplay: Boolean = true,
) {
    val shouldSuppressIsland: Boolean get() = !screenOn
}

/** How the island interacted with an event — metadata only, never notification bodies. */
enum class HistoryAction { SHOWN, EXPANDED, COLLAPSED, DISMISSED, EXPIRED, ACTION_TAPPED, SUPPRESSED }

/**
 * One row in the local history. Opt-in, metadata-first: [titlePreview] is only populated when
 * the user explicitly enabled "store notification content".
 */
data class HistoryEntry(
    val id: String,
    val atMs: Long,
    val type: IslandEventType,
    val priority: IslandPriority,
    val sourcePackage: String? = null,
    val sourceLabel: String? = null,
    val action: HistoryAction,
    val titlePreview: String? = null,
    val shownForMs: Long? = null,
)
