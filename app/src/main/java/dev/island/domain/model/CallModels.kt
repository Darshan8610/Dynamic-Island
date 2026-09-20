package dev.island.domain.model

/** Call lifecycle as exposed by Android's telephony APIs. */
enum class CallState {
    INCOMING,
    ACTIVE,
    HOLDING,
    ENDED,
    UNKNOWN,
}

/**
 * Call snapshot. Island only ever reflects state Android publishes to a normal app
 * (READ_PHONE_STATE): no call log, no audio, no recording, no interception.
 */
data class CallInfo(
    val state: CallState = CallState.UNKNOWN,
    /** Contact/handle as provided by the system. May be null when the number is restricted. */
    val handle: String? = null,
    val durationMs: Long = 0L,
    val durationSampledAtElapsedMs: Long = 0L,
    val canAnswer: Boolean = false,
    val canEnd: Boolean = false,
    val canMute: Boolean = false,
    val isMuted: Boolean = false,
) {
    fun durationAt(elapsedNowMs: Long): Long {
        if (state != CallState.ACTIVE) return durationMs
        return durationMs + (elapsedNowMs - durationSampledAtElapsedMs).coerceAtLeast(0L)
    }
}
