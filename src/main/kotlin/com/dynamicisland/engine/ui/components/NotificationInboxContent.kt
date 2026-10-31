package com.dynamicisland.engine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.engine.core.model.IslandNotification
import com.dynamicisland.engine.core.model.NotificationGroups
import com.dynamicisland.engine.ui.squircle.SquircleShape
import kotlin.math.abs

/**
 * Premium iOS-style notification inbox.
 *
 * The previous version was a flat list of white text rows on a near-transparent field:
 * no app identity, no hierarchy, no grouping, no visual weight. This redesign reproduces
 * the iOS notification stack: notifications grouped by app, each group headed by a squircle
 * app avatar and label, each row a rounded card with a semantic app glyph, a SemiBold title,
 * a secondary body line and a relative timestamp. A header carries the count and "Clear all".
 */
@Composable
fun NotificationInboxContent(
    notifications: List<IslandNotification>,
    collapsed: Boolean = false,
    onOpenNotification: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismissNotification: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val visible = if (collapsed) notifications.take(1) else notifications
    val groups = remember(visible) { NotificationGroups.group(visible) }
    val open: (IslandNotification) -> Unit = { onOpenNotification(it.key) }
    val dismiss: (IslandNotification) -> Unit = { onDismissNotification(it.key) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Notifications",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            if (visible.isNotEmpty()) {
                Text(
                    text = visible.size.toString(),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Clear all",
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable(onClick = onClearAll)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No notifications",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(groups, key = { it.key }) { group ->
                    NotificationGroup(group = group, onOpen = open, onDismiss = dismiss)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun NotificationGroup(
    group: NotificationGroups.Group,
    onOpen: (IslandNotification) -> Unit,
    onDismiss: (IslandNotification) -> Unit
) {
    val count = group.notifications.size
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppAvatar(label = group.label, size = 28.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (count > 1) {
                    Text(
                        text = "$count notifications",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        group.notifications.forEach { notification ->
            NotificationCard(
                notification = notification,
                onOpen = { onOpen(notification) },
                onDismiss = { onDismiss(notification) }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

/**
 * A single notification row: semantic app avatar, SemiBold title, secondary body and a
 * relative timestamp, all inside a rounded, bordered card. Tap opens; swipe left dismisses
 * with a finger-following slide and fade.
 */
@Composable
private fun NotificationCard(
    notification: IslandNotification,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale = if (pressed) 0.97f else 1f
    var offset by remember(notification.key) { mutableFloatStateOf(0f) }
    val thresholdPx = with(density) { 56.dp.toPx() }

    val title = notification.title.ifBlank { notification.appLabel ?: notification.packageName }
    val body = notification.body
    val time = relativeTime(notification.postedAtMillis)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                translationX = offset
                alpha = 1f - (abs(offset) / (thresholdPx * 3f)).coerceIn(0f, 0.6f)
            }
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .pointerInput(notification.key) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        offset += dragAmount
                    },
                    onDragEnd = {
                        if (offset < -thresholdPx) onDismiss()
                        else offset = 0f
                    },
                    onDragCancel = { offset = 0f }
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "Open notification"
            ) { onOpen() }
            .semantics { contentDescription = "Notification: $title" },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppAvatar(label = notification.appLabel ?: notification.packageName, size = 26.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!body.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = body,
                        color = Color.White.copy(alpha = 0.52f),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = time,
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/**
 * Squircle app avatar with the app's initial, tinted by a deterministic palette so each
 * package keeps a stable identity across recompositions and sessions.
 */
@Composable
private fun AppAvatar(label: String, size: Dp) {
    val color = remember(label) { appAvatarColor(label) }
    val initial = remember(label) {
        label.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(SquircleShape(size * 0.26f))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = (size.value * 0.46f).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private val APP_PALETTE = listOf(
    Color(0xFFFF453A), Color(0xFFFF9F0A), Color(0xFFFFD60A),
    Color(0xFF30D158), Color(0xFF32D74B), Color(0xFF0A84FF),
    Color(0xFF5E5CE6), Color(0xFFBF5AF2), Color(0xFFFF375F),
    Color(0xFF00C7BE), Color(0xFF64D2FF), Color(0xFFFF6482)
)

private fun appAvatarColor(label: String): Color {
    val h = label.hashCode()
    val idx = h.times(31).rem(APP_PALETTE.size)
    return APP_PALETTE[if (idx < 0) idx + APP_PALETTE.size else idx]
}

private fun relativeTime(millis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - millis
    if (diff < 0) return "now"
    val minutes = diff / 60_000L
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 1440 -> "${minutes / 60}h"
        else -> "${minutes / 1440}d"
    }
}
