package dev.island.data.history

import android.content.Context
import dev.island.core.logging.IslandLogger
import dev.island.core.logging.Redaction
import dev.island.domain.model.HistoryEntry
import dev.island.domain.model.IslandPriority
import dev.island.domain.model.IslandEventType
import dev.island.domain.model.HistoryAction
import dev.island.domain.repository.EventHistoryRepository
import dev.island.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local, opt-in event history.
 *
 * Design rules:
 * - metadata only unless the user explicitly enabled "store notification content";
 * - never backed up (see res/xml/backup_rules.xml);
 * - bounded in memory and on disk, pruned by the user's retention setting;
 * - writes are debounced so a burst of events produces one file write;
 * - loading is lazy: nothing is read until the history screen (or an export) asks for it.
 */
class FileEventHistoryRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val logger: IslandLogger,
    private val settingsRepository: SettingsRepository,
) : EventHistoryRepository {

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private val entriesFlow: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    override val entries = entriesFlow

    private val file: File get() = File(context.filesDir, FILE_NAME)
    private var loaded = false
    private var writeJob: Job? = null

    /** Called by the history screen; safe to call repeatedly. */
    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        scope.launch {
            val read = runCatching { readFromDisk() }
                .onFailure { logger.w(TAG, "history unreadable, starting empty", it) }
                .getOrDefault(emptyList())
            _entries.value = prune(read)
        }
    }

    override suspend fun record(entry: HistoryEntry) {
        val settings = settingsRepository.current()
        if (!settings.privacy.historyEnabled) return
        // Second guard: content is only stored when the user asked for it.
        val safe = if (settings.privacy.historyStoresContent) entry else entry.copy(titlePreview = null)

        ensureLoaded()
        val updated = (listOf(safe) + _entries.value).take(MAX_ENTRIES)
        val pruned = prune(updated, retentionDays = settings.privacy.historyRetentionDays)
        _entries.value = pruned
        scheduleWrite(pruned)
    }

    override suspend fun clear() {
        _entries.value = emptyList()
        writeJob?.cancel()
        runCatching { if (file.exists()) file.delete() }
            .onFailure { logger.w(TAG, "history delete failed", it) }
        logger.i(TAG, "history cleared")
    }

    override suspend fun exportAsText(): String {
        ensureLoaded()
        val settings = settingsRepository.current()
        val builder = StringBuilder()
        builder.append("Island event history export\n")
        builder.append("Entries: ${_entries.value.size}\n")
        builder.append("Content stored: ${settings.privacy.historyStoresContent}\n")
        builder.append("----\n")
        _entries.value.forEach { entry ->
            val time = java.text.DateFormat.getTimeInstance().format(java.util.Date(entry.atMs))
            val source = Redaction.safePackage(entry.sourcePackage)
            val title = if (settings.privacy.historyStoresContent) {
                entry.titlePreview?.let { " \"${Redaction.truncate(it, 40)}\"" } ?: ""
            } else {
                ""
            }
            builder.append("$time ${entry.type} ${entry.action} $source${title}\n")
        }
        return builder.toString()
    }

    private fun prune(list: List<HistoryEntry>, retentionDays: Int? = null): List<HistoryEntry> {
        val days = retentionDays ?: return list.take(MAX_ENTRIES)
        val cutoff = System.currentTimeMillis() - days * MILLIS_PER_DAY
        return list.filter { it.atMs >= cutoff }.take(MAX_ENTRIES)
    }

    private fun scheduleWrite(entries: List<HistoryEntry>) {
        writeJob?.cancel()
        writeJob = scope.launch {
            delay(WRITE_DEBOUNCE_MS)
            runCatching { writeToDisk(entries) }
                .onFailure { logger.w(TAG, "history write failed", it) }
        }
    }

    private fun writeToDisk(entries: List<HistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("at", entry.atMs)
                    put("type", entry.type.name)
                    put("priority", entry.priority.name)
                    put("pkg", entry.sourcePackage ?: JSONObject.NULL)
                    put("label", entry.sourceLabel ?: JSONObject.NULL)
                    put("action", entry.action.name)
                    put("preview", entry.titlePreview ?: JSONObject.NULL)
                    put("shownFor", entry.shownForMs ?: JSONObject.NULL)
                },
            )
        }
        file.writeText(array.toString())
    }

    private fun readFromDisk(): List<HistoryEntry> {
        if (!file.exists()) return emptyList()
        val raw = file.readText()
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val entry = runCatching {
                    HistoryEntry(
                        id = obj.getString("id"),
                        atMs = obj.optLong("at"),
                        type = enumValueOf<IslandEventType>(obj.optString("type", IslandEventType.SYSTEM.name)),
                        priority = enumValueOf<IslandPriority>(obj.optString("priority", IslandPriority.LOW.name)),
                        sourcePackage = obj.optString("pkg").takeIf { it.isNotBlank() && it != "null" },
                        sourceLabel = obj.optString("label").takeIf { it.isNotBlank() && it != "null" },
                        action = enumValueOf<HistoryAction>(obj.optString("action", HistoryAction.SHOWN.name)),
                        titlePreview = obj.optString("preview").takeIf { it.isNotBlank() && it != "null" },
                        shownForMs = obj.optLong("shownFor").takeIf { it > 0 },
                    )
                }.getOrNull()
                if (entry != null) add(entry)
            }
        }
    }

    companion object {
        private const val TAG = "HistoryRepo"
        private const val FILE_NAME = "island_history.json"
        private const val MAX_ENTRIES = 500
        private const val WRITE_DEBOUNCE_MS = 1_500L
        private const val MILLIS_PER_DAY = 86_400_000L
    }
}
