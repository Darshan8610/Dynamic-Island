package dev.island.core.logging

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class IslandLogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

/**
 * One line in the in-app event log. The engine never logs notification bodies, message text,
 * contact handles or any other content — see [Redaction] and README.md → "Privacy".
 */
data class LogEntry(
    val id: Long,
    val atMs: Long,
    val level: IslandLogLevel,
    val tag: String,
    val message: String,
)

/** Sink for diagnostics. Implemented by [AndroidIslandLogger] (logcat + ring buffer). */
interface IslandLogger {
    fun log(level: IslandLogLevel, tag: String, message: String, error: Throwable? = null)

    fun v(tag: String, message: String) = log(IslandLogLevel.VERBOSE, tag, message)
    fun d(tag: String, message: String) = log(IslandLogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(IslandLogLevel.INFO, tag, message)
    fun w(tag: String, message: String, error: Throwable? = null) = log(IslandLogLevel.WARN, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = log(IslandLogLevel.ERROR, tag, message, error)
}

/** No-op logger used by unit tests and by release builds with the event logger disabled. */
object SilentIslandLogger : IslandLogger {
    override fun log(level: IslandLogLevel, tag: String, message: String, error: Throwable?) = Unit
}

/**
 * Bounded, in-memory ring buffer behind the "Event log" developer screen. Deliberately not
 * persisted: a log that survives process death would also survive the user clearing it.
 */
class DebugLogStore(private val capacity: Int = DEFAULT_CAPACITY) {

    private val entries = ArrayDeque<LogEntry>()
    private var nextId = 0L
    private val listeners = mutableListOf<(List<LogEntry>) -> Unit>()

    @Synchronized
    fun add(entry: LogEntry) {
        entries.addLast(entry.copy(id = nextId++))
        while (entries.size > capacity) entries.removeFirst()
        notifyListeners()
    }

    @Synchronized
    fun snapshot(): List<LogEntry> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
        notifyListeners()
    }

    @Synchronized
    fun addListener(listener: (List<LogEntry>) -> Unit): () -> Unit {
        listeners += listener
        listener(entries.toList())
        return { synchronized(this) { listeners -= listener } }
    }

    private fun notifyListeners() {
        val copy = entries.toList()
        listeners.forEach { it(copy) }
    }

    companion object {
        const val DEFAULT_CAPACITY = 500
    }
}

/** Turns a [DebugLogStore] into a cold flow for Compose, without pulling in a coroutine dependency here. */
fun DebugLogStore.asFlow(): Flow<List<LogEntry>> = callbackFlow {
    val unsubscribe = addListener { trySend(it) }
    awaitClose { unsubscribe() }
}

/**
 * Redaction helpers. Anything that could contain user content goes through here before it is
 * written to a log or an exported file.
 */
object Redaction {

    private const val PLACEHOLDER = "[redacted]"

    /** Keeps the first [keep] characters so a log line stays useful without exposing content. */
    fun truncate(text: String?, keep: Int = 24): String? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
        return if (trimmed.length <= keep) trimmed else trimmed.take(keep) + "…"
    }

    fun redact(text: String?): String = if (text.isNullOrBlank()) "" else PLACEHOLDER

    /** `Mom (mobile)` → `M…`; used for contact handles. */
    fun redactHandle(handle: String?): String? {
        if (handle.isNullOrBlank()) return null
        val first = handle.trim().firstOrNull() ?: return null
        return "$first…"
    }

    /** Package names are not content and are safe to log. */
    fun safePackage(pkg: String?): String = pkg ?: "unknown"
}
