package dev.island.core.logging

import android.util.Log

/**
 * Logcat + bounded in-memory ring buffer.
 *
 * Nothing here ever receives notification bodies, message text or contact handles: the engine
 * logs identifiers, types and priorities only. The export path applies [Redaction] again as a
 * second line of defence.
 */
class AndroidIslandLogger(
    private val store: DebugLogStore,
    private val verboseEnabled: () -> Boolean = { true },
) : IslandLogger {

    override fun log(level: IslandLogLevel, tag: String, message: String, error: Throwable?) {
        if (level == IslandLogLevel.VERBOSE && !verboseEnabled()) return
        when (level) {
            IslandLogLevel.VERBOSE -> Log.v(tag, message, error)
            IslandLogLevel.DEBUG -> Log.d(tag, message, error)
            IslandLogLevel.INFO -> Log.i(tag, message, error)
            IslandLogLevel.WARN -> Log.w(tag, message, error)
            IslandLogLevel.ERROR -> Log.e(tag, message, error)
        }
        store.add(
            LogEntry(
                id = 0L,
                atMs = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = error?.let { "$message — ${it.javaClass.simpleName}" } ?: message,
            ),
        )
    }
}
