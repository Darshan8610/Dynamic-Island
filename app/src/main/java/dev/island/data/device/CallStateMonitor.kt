package dev.island.data.device

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dev.island.core.logging.IslandLogger
import dev.island.domain.engine.IslandClock
import dev.island.domain.model.CallInfo
import dev.island.domain.model.CallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Call state through the documented telephony APIs.
 *
 * What Island does:
 * - listens for `CALL_STATE_RINGING / OFFHOOK / IDLE` (READ_PHONE_STATE, requested only when the
 *   user turns on the call island);
 * - measures call duration from the OFFHOOK transition with a monotonic clock;
 * - optionally answers/ends a call through [TelecomManager] when the user granted
 *   ANSWER_PHONE_CALLS, and mutes the microphone through [AudioManager].
 *
 * What Island deliberately does NOT do: read the call log, resolve contact names, record audio or
 * intercept anything. The caller's name therefore only appears when the dialer itself posts a
 * call-style notification (handled by the notification pipeline) — documented in README.md.
 */
@Suppress("DEPRECATION")
class CallStateMonitor(
    private val context: Context,
    private val logger: IslandLogger,
    private val clock: IslandClock,
) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val telecomManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        } else {
            null
        }
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _call = MutableStateFlow(CallInfo())
    val call: StateFlow<CallInfo> = _call.asStateFlow()

    private var started = false
    private var telephonyCallback: TelephonyCallback? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var callStartedAtElapsedMs = 0L

    fun start() {
        if (started) return
        val tm = telephonyManager
        if (tm == null) {
            logger.d(TAG, "no telephony on this device; call island disabled")
            return
        }
        if (!hasReadPhoneState()) {
            logger.i(TAG, "READ_PHONE_STATE missing; call island disabled")
            return
        }
        started = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = onStateChanged(state)
            }
            runCatching { tm.registerTelephonyCallback(context.mainExecutor, callback) }
                .onSuccess { telephonyCallback = callback }
                .onFailure { logger.w(TAG, "telephony callback registration failed", it) }
        } else {
            val listener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = onStateChanged(state)
            }
            runCatching { tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE) }
                .onSuccess { phoneStateListener = listener }
                .onFailure { logger.w(TAG, "phone state listener failed", it) }
        }
        logger.i(TAG, "call monitor started")
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
            } else {
                phoneStateListener?.let { telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE) }
            }
        }.onFailure { logger.w(TAG, "call monitor stop failed", it) }
        telephonyCallback = null
        phoneStateListener = null
        _call.value = CallInfo()
        logger.i(TAG, "call monitor stopped")
    }

    private fun onStateChanged(state: Int) {
        val now = clock.elapsedMs()
        val next = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> CallInfo(
                state = CallState.INCOMING,
                handle = null,
                durationMs = 0L,
                durationSampledAtElapsedMs = now,
                canAnswer = canControlCalls(),
                canEnd = canControlCalls(),
                canMute = false,
                isMuted = false,
            )

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (callStartedAtElapsedMs == 0L) callStartedAtElapsedMs = now
                CallInfo(
                    state = CallState.ACTIVE,
                    handle = null,
                    durationMs = now - callStartedAtElapsedMs,
                    durationSampledAtElapsedMs = now,
                    canAnswer = false,
                    canEnd = canControlCalls(),
                    canMute = canMute(),
                    isMuted = isMuted(),
                )
            }

            else -> {
                val previous = _call.value
                callStartedAtElapsedMs = 0L
                if (previous.state == CallState.UNKNOWN) {
                    CallInfo()
                } else {
                    CallInfo(
                        state = CallState.ENDED,
                        handle = null,
                        durationMs = previous.durationAt(now),
                        durationSampledAtElapsedMs = now,
                    )
                }
            }
        }
        _call.value = next
        logger.d(TAG, "call state -> ${next.state}")
    }

    // region controls (only exposed when Android permits them)

    fun canControlCalls(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            hasPermission(android.Manifest.permission.ANSWER_PHONE_CALLS)

    fun canMute(): Boolean = hasPermission(android.Manifest.permission.MODIFY_AUDIO_SETTINGS)

    fun isMuted(): Boolean = runCatching { audioManager?.isMicrophoneMute ?: false }.getOrDefault(false)

    // Guarded by canControlCalls(), which checks ANSWER_PHONE_CALLS.
    @SuppressLint("MissingPermission")
    fun answer() {
        if (!canControlCalls()) {
            logger.w(TAG, "answer ignored: ANSWER_PHONE_CALLS not granted")
            return
        }
        runCatching { telecomManager?.acceptRingingCall() }
            .onFailure { logger.w(TAG, "answer failed", it) }
    }

    // Guarded by canControlCalls(), which checks ANSWER_PHONE_CALLS.
    @SuppressLint("MissingPermission")
    fun endCall() {
        if (!canControlCalls()) {
            logger.w(TAG, "end ignored: ANSWER_PHONE_CALLS not granted")
            return
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) telecomManager?.endCall() else null
        }.onFailure { logger.w(TAG, "endCall failed", it) }
    }

    fun toggleMute() {
        if (!canMute()) {
            logger.w(TAG, "mute ignored: MODIFY_AUDIO_SETTINGS not granted")
            return
        }
        runCatching {
            val target = !isMuted()
            audioManager?.isMicrophoneMute = target
            _call.value = _call.value.copy(isMuted = target)
        }.onFailure { logger.w(TAG, "mute toggle failed", it) }
    }

    // endregion

    private fun hasReadPhoneState(): Boolean = hasPermission(android.Manifest.permission.READ_PHONE_STATE)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun isAvailable(): Boolean = telephonyManager != null

    companion object {
        private const val TAG = "CallMonitor"
    }
}
