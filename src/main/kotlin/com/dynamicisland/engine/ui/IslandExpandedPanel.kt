package com.dynamicisland.engine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dynamicisland.engine.core.bus.IslandUserCommand
import com.dynamicisland.engine.core.bus.SystemIngestionEvent
import com.dynamicisland.engine.core.model.*
import com.dynamicisland.engine.ui.components.*

/** The live overlay dashboard. Only its header handles collapse; controls own their taps. */
@Composable
fun IslandExpandedPanel(
    state: IslandState, playback: MediaPlaybackSnapshot,
    call: SystemIngestionEvent.Telecom.CallStateChanged?, notificationCount: Int,
    onClose: () -> Unit, onOpenApp: () -> Unit, onOpenSource: (IslandActivity) -> Unit,
    onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onSeek: (Long) -> Unit,
    onCommand: (IslandUserCommand) -> Unit, onAnswer: () -> Unit, onHangup: () -> Unit,
    onMute: () -> Unit, onSpeaker: () -> Unit, actionStatus: String? = null
) {
    val activity = (state as? IslandState.Expanded)?.activity
    val close by rememberUpdatedState(onClose)
    val contentHeight = (200 + (LocalDensity.current.fontScale.coerceIn(1f, 2f) - 1) * 100).dp
    MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, surface = Color.Black)) {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Color.Black)
            .semantics { contentDescription = "Expanded island" }) {
            Row(Modifier.fillMaxWidth().height(48.dp).pointerInput(Unit) {
                var distance = 0f
                detectVerticalDragGestures(onDragStart = { distance = 0f },
                    onVerticalDrag = { change, amount -> change.consume(); distance += amount },
                    onDragEnd = { if (distance < -24.dp.toPx()) close() })
            }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (activity?.kind == ActivityKind.MEDIA_SESSION) "Now playing" else "Dynamic Island",
                    style = MaterialTheme.typography.labelLarge, color = Color(0xFFB7BABD),
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onClose)
                    .semantics { contentDescription = "Collapse island" }, contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width * .2f, size.height * .4f)
                            lineTo(size.width * .5f, size.height * .7f)
                            lineTo(size.width * .8f, size.height * .4f)
                        }
                        drawPath(path, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                }
            }
            if (state is IslandState.NotificationInbox) {
                NotificationInboxContent(state.notifications,
                    onOpenNotification = { onCommand(IslandUserCommand.OpenNotification(it)) },
                    onClearAll = { onCommand(IslandUserCommand.ClearInbox) },
                    onDismissNotification = { onCommand(IslandUserCommand.DismissNotification(it)) },
                    modifier = Modifier.weight(1f))
            } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                when {
                    activity?.kind == ActivityKind.MEDIA_SESSION -> key(activity.ownerPackage, activity.title) { MediaExpandedContent(
                        activity.title, activity.subtitle,
                        (activity.compactLeading as? IslandSlotContent.Icon)?.iconBitmap, playback,
                        onPlayPause, onNext, onPrevious, onSeek, notificationCount,
                        onOpenInbox = { onCommand(IslandUserCommand.OpenInbox) },
                        modifier = Modifier.height(contentHeight)) }
                    call != null && activity?.kind in setOf(ActivityKind.LIVE_CALL, ActivityKind.RINGING_CALL, ActivityKind.EMERGENCY_CALL) ->
                        CallExpandedContent(call.callerName ?: "Phone call", call.callerNumber,
                            "${call.durationSeconds / 60}:${(call.durationSeconds % 60).toString().padStart(2, '0')}",
                            call.status == SystemIngestionEvent.Telecom.CallStatus.INCOMING_RINGING,
                            call.isMicrophoneMuted, call.isSpeakerphoneOn,
                            onAnswer = onAnswer, onHangup = onHangup, onToggleMute = onMute,
                            onToggleSpeaker = onSpeaker, isControllable = call.isControllable,
                            isOnHold = call.status == SystemIngestionEvent.Telecom.CallStatus.ON_HOLD,
                            modifier = Modifier.height(contentHeight))
                    else -> Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(activity?.title ?: "You're all caught up", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        activity?.subtitle?.let { Text(it, color = Color.LightGray) }
                        if (activity != null) {
                            TimerActions.cancellation(activity)?.let {
                                TextButton(onClick = { onCommand(IslandUserCommand.CancelTimer(activity.id)) }) { Text("Cancel timer") }
                            }
                            if (activity.deepLinkIntent != null) TextButton(onClick = { onOpenSource(activity) }) { Text("Open app") }
                        }
                        if (notificationCount > 0) TextButton(onClick = { onCommand(IslandUserCommand.OpenInbox) }) { Text("Notifications · $notificationCount") }
                        if (activity == null) TextButton(onClick = onOpenApp) { Text("Island settings") }
                    }
                }
                actionStatus?.let { Text(it, color = Color.LightGray, modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}
