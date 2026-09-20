package dev.island.feature.app.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.island.R
import dev.island.core.AppGraph
import dev.island.core.permissions.PermissionIntents
import dev.island.domain.model.PermissionKey
import dev.island.domain.model.PermissionStatus

/**
 * Permission UI: one row per capability, each with its own fix path.
 *
 * Two kinds of permission exist and they are handled differently on purpose:
 * - runtime permissions (notifications, Bluetooth names, phone state) → the system dialog through
 *   [rememberLauncherForActivityResult];
 * - special access (overlay, notification listener, exact alarms, battery optimisation) → the
 *   matching system settings screen, because Android offers no in-app dialog for them.
 *
 * Nothing is ever requested silently, and losing an optional permission only disables the feature
 * it belongs to — the island itself keeps working.
 */

/** Runtime permission string for keys that have one; null for special-access keys. */
fun runtimePermissionFor(key: PermissionKey): String? = when (key) {
    PermissionKey.POST_NOTIFICATIONS -> android.Manifest.permission.POST_NOTIFICATIONS
    PermissionKey.BLUETOOTH_CONNECT -> android.Manifest.permission.BLUETOOTH_CONNECT
    PermissionKey.READ_PHONE_STATE -> android.Manifest.permission.READ_PHONE_STATE
    PermissionKey.ANSWER_PHONE_CALLS -> android.Manifest.permission.ANSWER_PHONE_CALLS
    PermissionKey.OVERLAY,
    PermissionKey.NOTIFICATION_LISTENER,
    PermissionKey.EXACT_ALARM,
    PermissionKey.BATTERY_OPTIMIZATION,
    -> null
}

/** True when this permission can be requested on the current API level at all. */
fun permissionAppliesOnThisDevice(key: PermissionKey): Boolean = when (key) {
    // POST_NOTIFICATIONS is a runtime permission from API 33; below that it is granted at install.
    PermissionKey.POST_NOTIFICATIONS -> android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU

    // BLUETOOTH_CONNECT exists from API 31; older releases use the install-time BLUETOOTH permission.
    PermissionKey.BLUETOOTH_CONNECT -> android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

    // Exact-alarm special access is a concept from API 31 onwards.
    PermissionKey.EXACT_ALARM -> android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    else -> true
}

@Composable
fun permissionTitle(key: PermissionKey): String = stringResource(
    when (key) {
        PermissionKey.OVERLAY -> R.string.permission_overlay_title
        PermissionKey.NOTIFICATION_LISTENER -> R.string.permission_listener_title
        PermissionKey.POST_NOTIFICATIONS -> R.string.permission_post_notifications_title
        PermissionKey.BLUETOOTH_CONNECT -> R.string.permission_bluetooth_title
        PermissionKey.READ_PHONE_STATE -> R.string.permission_phone_title
        PermissionKey.ANSWER_PHONE_CALLS -> R.string.permission_phone_title
        PermissionKey.EXACT_ALARM -> R.string.permission_exact_alarm_title
        PermissionKey.BATTERY_OPTIMIZATION -> R.string.permission_battery_title
    },
)

@Composable
fun permissionBody(key: PermissionKey): String = stringResource(
    when (key) {
        PermissionKey.OVERLAY -> R.string.permission_overlay_body
        PermissionKey.NOTIFICATION_LISTENER -> R.string.permission_listener_body
        PermissionKey.POST_NOTIFICATIONS -> R.string.permission_post_notifications_body
        PermissionKey.BLUETOOTH_CONNECT -> R.string.permission_bluetooth_body
        PermissionKey.READ_PHONE_STATE -> R.string.permission_phone_body
        PermissionKey.ANSWER_PHONE_CALLS -> R.string.permission_phone_body
        PermissionKey.EXACT_ALARM -> R.string.permission_exact_alarm_body
        PermissionKey.BATTERY_OPTIMIZATION -> R.string.permission_battery_body
    },
)

/** Settings intent for special access, or null when the key is a runtime permission. */
fun specialAccessIntent(context: Context, key: PermissionKey) = when (key) {
    PermissionKey.OVERLAY -> PermissionIntents.overlaySettings(context)
    PermissionKey.NOTIFICATION_LISTENER -> PermissionIntents.notificationListenerSettings(context)
    PermissionKey.EXACT_ALARM -> PermissionIntents.exactAlarmSettings(context)
    PermissionKey.BATTERY_OPTIMIZATION -> PermissionIntents.batteryOptimizationSettings(context)
    else -> null
}

@Composable
fun PermissionRow(
    status: PermissionStatus,
    modifier: Modifier = Modifier,
    onChanged: () -> Unit = { AppGraph.permissionRepository.refresh() },
) {
    val context = LocalContext.current
    val key = status.key
    val runtimePermission = runtimePermissionFor(key)
    val title = permissionTitle(key)
    val body = permissionBody(key)

    val runtimeLauncher = if (runtimePermission != null) {
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }
    } else {
        null
    }

    val tone = when {
        status.granted -> StatusTone.OK
        status.severity == PermissionStatus.Severity.ERROR -> StatusTone.ERROR
        else -> StatusTone.WARN
    }
    val chipText = stringResource(
        when {
            status.granted -> R.string.dashboard_state_granted
            status.severity == PermissionStatus.Severity.ERROR -> R.string.dashboard_state_missing
            else -> R.string.dashboard_state_partial
        },
    )
    val fixLabel = stringResource(
        if (status.granted) R.string.action_open else R.string.action_fix,
    )

    ClickRow(
        title = title,
        summary = body,
        modifier = modifier,
        onClick = {
            when {
                runtimePermission != null && !status.granted -> runtimeLauncher?.launch(runtimePermission)
                else -> {
                    val intent = specialAccessIntent(context, key) ?: PermissionIntents.appDetailsSettings(context)
                    AppGraph.permissionLauncher.launch(context, intent)
                }
            }
            // Special-access screens return without a callback; re-read state when we resume.
            onChanged()
        },
        trailing = {
            StatusChip(text = if (status.granted) chipText else "$chipText · $fixLabel", tone = tone)
        },
    )
}
