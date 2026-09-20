package dev.island.data.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.island.core.logging.IslandLogger
import dev.island.core.logging.Redaction
import dev.island.domain.model.ConnectedDeviceKind
import dev.island.domain.model.ConnectedDeviceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Connection events for audio devices, watches and car systems.
 *
 * Island never scans: it only listens for `ACTION_ACL_CONNECTED` / `ACTION_ACL_DISCONNECTED`
 * (already-connected device lifecycle) and `ACTION_HEADSET_PLUG` (wired audio). Device *names*
 * need BLUETOOTH_CONNECT on Android 12+; without it the event still fires with a generic label.
 */
class BluetoothMonitor(
    private val context: Context,
    private val logger: IslandLogger,
) {

    val connections: Flow<ConnectedDeviceInfo> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        val device = intent.deviceExtra()
                        trySend(
                            ConnectedDeviceInfo(
                                kind = classify(device),
                                connected = true,
                                name = deviceName(device),
                                address = null, // never surfaced; MAC addresses are identifiers
                            ),
                        )
                    }

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val device = intent.deviceExtra()
                        trySend(
                            ConnectedDeviceInfo(
                                kind = classify(device),
                                connected = false,
                                name = deviceName(device),
                                address = null,
                            ),
                        )
                    }

                    Intent.ACTION_HEADSET_PLUG -> {
                        val state = intent.getIntExtra(EXTRA_HEADSET_STATE, -1)
                        if (state < 0) return
                        val name = intent.getStringExtra(EXTRA_HEADSET_NAME)?.takeIf { it.isNotBlank() }
                        trySend(
                            ConnectedDeviceInfo(
                                kind = ConnectedDeviceKind.WIRED_HEADSET,
                                connected = state == 1,
                                name = name,
                                address = null,
                            ),
                        )
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        runCatching {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
            .onFailure { logger.w(TAG, "bluetooth receiver registration failed", it) }

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
                .onFailure { logger.d(TAG, "bluetooth receiver already unregistered") }
        }
    }

    /** Devices currently routed for audio — used to seed state after a reconnect. */
    fun currentAudioRoute(): ConnectedDeviceKind? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        return runCatching {
            when {
                audioManager.isBluetoothA2dpOn -> ConnectedDeviceKind.BLUETOOTH_AUDIO
                audioManager.isWiredHeadsetOn -> ConnectedDeviceKind.WIRED_HEADSET
                else -> null
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun Intent.deviceExtra(): BluetoothDevice? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        }
    }.onFailure { logger.d(TAG, "no device extra on connection broadcast") }.getOrNull()

    // Guarded by hasConnectPermission() above; lint does not follow helper methods.
    @SuppressLint("MissingPermission")
    private fun classify(device: BluetoothDevice?): ConnectedDeviceKind {
        if (device == null) return ConnectedDeviceKind.BLUETOOTH_AUDIO
        // Every BluetoothDevice accessor needs BLUETOOTH_CONNECT on Android 12+.
        if (!hasConnectPermission()) return ConnectedDeviceKind.BLUETOOTH_AUDIO
        return runCatching {
            val bluetoothClass = device.bluetoothClass
            when (bluetoothClass?.majorDeviceClass) {
                BluetoothClassMajor.AUDIO_VIDEO -> when (bluetoothClass.deviceClass) {
                    BluetoothClassAudio.HEADPHONES,
                    BluetoothClassAudio.EARPIECE,
                    BluetoothClassAudio.WEARABLE_HEADSET_DEVICE,
                    -> ConnectedDeviceKind.BLUETOOTH_AUDIO

                    BluetoothClassAudio.LOUDSPEAKER,
                    BluetoothClassAudio.PORTABLE_AUDIO,
                    BluetoothClassAudio.HIFI_AUDIO,
                    -> ConnectedDeviceKind.SPEAKER

                    else -> ConnectedDeviceKind.BLUETOOTH_AUDIO
                }

                BluetoothClassMajor.WEARABLE -> ConnectedDeviceKind.WATCH
                BluetoothClassMajor.IMAGING -> ConnectedDeviceKind.OTHER
                BluetoothClassMajor.PHONE -> ConnectedDeviceKind.OTHER
                else -> when (device.type) {
                    BluetoothDevice.DEVICE_TYPE_LE -> ConnectedDeviceKind.WATCH
                    else -> ConnectedDeviceKind.BLUETOOTH_AUDIO
                }
            }
        }.getOrElse {
            logger.d(TAG, "device class unavailable (${it.javaClass.simpleName})")
            ConnectedDeviceKind.BLUETOOTH_AUDIO
        }
    }

    /** Requires BLUETOOTH_CONNECT on Android 12+; returns null instead of throwing when missing. */
    @SuppressLint("MissingPermission")
    private fun deviceName(device: BluetoothDevice?): String? {
        if (device == null) return null
        if (!hasConnectPermission()) {
            logger.d(TAG, "BLUETOOTH_CONNECT missing: using generic device label")
            return null
        }
        return runCatching { device.name?.takeIf { it.isNotBlank() } }
            .onFailure { logger.w(TAG, "device name unavailable", it) }
            .getOrNull()
            ?.let { Redaction.truncate(it, 32) }
    }

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /** Whether this device has any Bluetooth capability at all (used to hide dead settings rows). */
    fun hasBluetoothHardware(): Boolean = runCatching {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter != null
    }.getOrDefault(false)

    companion object {
        private const val TAG = "BluetoothMonitor"
        private const val EXTRA_HEADSET_STATE = "state"
        private const val EXTRA_HEADSET_NAME = "name"

        // BluetoothClass constants mirrored to keep the mapping readable.
        private object BluetoothClassMajor {
            const val AUDIO_VIDEO = android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO
            const val WEARABLE = android.bluetooth.BluetoothClass.Device.Major.WEARABLE
            const val IMAGING = android.bluetooth.BluetoothClass.Device.Major.IMAGING
            const val PHONE = android.bluetooth.BluetoothClass.Device.Major.PHONE
        }

        private object BluetoothClassAudio {
            const val HEADPHONES = android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES
            const val EARPIECE = android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
            const val WEARABLE_HEADSET_DEVICE =
                android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET
            const val LOUDSPEAKER = android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER
            const val PORTABLE_AUDIO = android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO
            const val HIFI_AUDIO = android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO
        }
    }
}
