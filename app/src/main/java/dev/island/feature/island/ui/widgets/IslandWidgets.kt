package dev.island.feature.island.ui.widgets

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HeadsetMic
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.island.R
import dev.island.core.ui.theme.IslandCorners
import dev.island.core.ui.theme.IslandColors
import dev.island.core.ui.theme.IslandDimensions
import dev.island.core.ui.theme.IslandMotion
import dev.island.core.ui.theme.IslandSpacing
import dev.island.core.ui.theme.IslandTypography
import dev.island.domain.model.ActionLabelKey
import dev.island.domain.model.IslandAction
import dev.island.domain.model.IslandActionKind
import dev.island.domain.model.IslandIconKey
import dev.island.feature.island.ui.renderers.LocalIslandRender

/** Icon vocabulary → vector. All vectors, all tintable, all with content descriptions. */
fun iconFor(key: IslandIconKey): ImageVector = when (key) {
    IslandIconKey.APP -> Icons.Rounded.Android
    IslandIconKey.NOTIFICATION -> Icons.Rounded.Notifications
    IslandIconKey.MUSIC -> Icons.Rounded.MusicNote
    IslandIconKey.TIMER -> Icons.Rounded.Timer
    IslandIconKey.STOPWATCH -> Icons.Rounded.Schedule
    IslandIconKey.CALL_INCOMING -> Icons.Rounded.RingVolume
    IslandIconKey.CALL_ACTIVE -> Icons.Rounded.Call
    IslandIconKey.CALL_ENDED -> Icons.Rounded.CallEnd
    IslandIconKey.BATTERY_CHARGING -> Icons.Rounded.BatteryChargingFull
    IslandIconKey.BATTERY_FULL -> Icons.Rounded.BatteryFull
    IslandIconKey.BATTERY_LOW -> Icons.Rounded.BatteryAlert
    IslandIconKey.HEADPHONES -> Icons.Rounded.HeadsetMic
    IslandIconKey.BLUETOOTH -> Icons.Rounded.BluetoothConnected
    IslandIconKey.WATCH -> Icons.Rounded.Watch
    IslandIconKey.NAVIGATION -> Icons.Rounded.Navigation
    IslandIconKey.DOWNLOAD -> Icons.Rounded.FileDownload
    IslandIconKey.ALARM -> Icons.Rounded.Alarm
    IslandIconKey.INFO -> Icons.Rounded.Info
    IslandIconKey.WARNING -> Icons.Rounded.Warning
    IslandIconKey.CUSTOM -> Icons.Rounded.AutoAwesome
}

fun actionIconFor(action: IslandAction): ImageVector = when (action.kind) {
    IslandActionKind.PLAY_PAUSE ->
        if (action.labelKey == ActionLabelKey.PAUSE) Icons.Rounded.Pause else Icons.Rounded.PlayArrow

    IslandActionKind.NEXT -> Icons.Rounded.SkipNext
    IslandActionKind.PREVIOUS -> Icons.Rounded.SkipPrevious
    IslandActionKind.ANSWER_CALL -> Icons.Rounded.Call
    IslandActionKind.END_CALL -> Icons.Rounded.CallEnd
    IslandActionKind.MUTE_CALL -> Icons.Rounded.MicOff
    IslandActionKind.STOPWATCH_LAP -> Icons.Rounded.Flag
    IslandActionKind.STOPWATCH_RESET, IslandActionKind.TIMER_RESET -> Icons.Rounded.Refresh
    IslandActionKind.TIMER_STOP -> Icons.Rounded.Stop
    IslandActionKind.STOPWATCH_PAUSE_RESUME ->
        if (action.labelKey == ActionLabelKey.PAUSE) Icons.Rounded.Pause else Icons.Rounded.PlayArrow

    IslandActionKind.ALARM_SNOOZE, IslandActionKind.TIMER_SNOOZE -> Icons.Rounded.Snooze
    IslandActionKind.OPEN_SOURCE_APP, IslandActionKind.OPEN_ISLAND -> Icons.Rounded.OpenInNew
    IslandActionKind.DISMISS -> Icons.Rounded.Close
    else -> iconFor(action.iconKey)
}

@Composable
fun actionLabel(action: IslandAction): String = when (action.labelKey) {
    ActionLabelKey.PLAY -> stringResource(R.string.action_play)
    ActionLabelKey.PAUSE -> stringResource(R.string.action_pause)
    ActionLabelKey.NEXT -> stringResource(R.string.action_next)
    ActionLabelKey.PREVIOUS -> stringResource(R.string.action_previous)
    ActionLabelKey.OPEN -> stringResource(R.string.action_open)
    ActionLabelKey.DISMISS -> stringResource(R.string.action_dismiss)
    ActionLabelKey.ANSWER -> stringResource(R.string.action_answer)
    ActionLabelKey.END -> stringResource(R.string.action_end)
    ActionLabelKey.MUTE -> stringResource(R.string.action_mute)
    ActionLabelKey.UNMUTE -> stringResource(R.string.action_unmute)
    ActionLabelKey.PAUSE_TIMER -> stringResource(R.string.action_pause)
    ActionLabelKey.RESUME -> stringResource(R.string.action_resume)
    ActionLabelKey.STOP -> stringResource(R.string.action_stop)
    ActionLabelKey.RESET -> stringResource(R.string.action_reset)
    ActionLabelKey.LAP -> stringResource(R.string.action_lap)
    ActionLabelKey.SNOOZE -> stringResource(R.string.action_snooze)
    null -> action.label ?: ""
}

/** Compact icon used in the pill. */
@Composable
fun IslandIcon(
    iconKey: IslandIconKey,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = IslandColors.TextPrimary,
    size: Dp = IslandDimensions.iconSize,
) {
    Icon(
        imageVector = iconFor(iconKey),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/** Source app icon (notification events); falls back to the generic island mark. */
@Composable
fun IslandAppIcon(
    packageName: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = IslandDimensions.appIconSize,
    shape: Shape = CircleShape,
) {
    val context = LocalIslandRender.current
    val drawable = remember(packageName) { context.appIcon(packageName) }
    if (drawable == null) {
        IslandIcon(
            iconKey = IslandIconKey.APP,
            contentDescription = contentDescription,
            modifier = modifier,
            size = size,
        )
        return
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        Image(
            painter = rememberDrawablePainter(drawable),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Album art, or a music glyph when the session publishes none. */
@Composable
fun IslandArtwork(
    token: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = IslandCorners.artwork,
) {
    val context = LocalIslandRender.current
    val bitmap: Bitmap? = remember(token) { context.artwork(token) }
    val description = stringResource(R.string.a11y_album_art)
    if (bitmap == null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .background(IslandColors.ElevatedDark),
            contentAlignment = Alignment.Center,
        ) {
            IslandIcon(
                iconKey = IslandIconKey.MUSIC,
                contentDescription = description,
                tint = IslandColors.TextSecondary,
                size = size * 0.5f,
            )
        }
        return
    }
    val image: ImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    Image(
        painter = BitmapPainter(image),
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius)),
    )
}

/** Thin, precise progress track — no Material chunk, no glow. */
@Composable
fun IslandProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = IslandDimensions.progressHeight,
    color: Color = IslandColors.TextPrimary,
    trackColor: Color = IslandColors.TrackDark,
    speed: Float = 1f,
) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(trackColor),
    ) {
        if (progress == null) {
            IndeterminateBar(color = color, speed = speed)
        } else {
            val animated by animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = tween(PROGRESS_ANIMATION_MS, easing = LinearEasing),
                label = "island-progress",
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(shape)
                    .background(color),
            )
        }
    }
}

private const val PROGRESS_ANIMATION_MS = 220

@Composable
private fun IndeterminateBar(color: Color, speed: Float) {
    val transition = rememberInfiniteTransition(label = "island-indeterminate")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween((INDETERMINATE_PERIOD_MS / speed.coerceIn(0.4f, 2f)).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "island-indeterminate-fraction",
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val barWidth = size.width * 0.35f
        val travel = size.width + barWidth
        val left = fraction * travel - barWidth
        drawRoundRect(
            color = color,
            topLeft = Offset(left, 0f),
            size = Size(barWidth, size.height),
            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
        )
    }
}

private const val INDETERMINATE_PERIOD_MS = 1400

/** Three bars that breathe while audio plays. Paused = static bars, zero animation cost. */
@Composable
fun PlayingIndicator(
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = IslandColors.TextPrimary,
    barWidth: Dp = IslandDimensions.waveformBarWidth,
    barHeight: Dp = IslandDimensions.waveformBarHeight,
    reduceMotion: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "island-waveform")
    val phase1 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(IslandMotion.waveformPeriodMs), RepeatMode.Reverse),
        label = "wave1",
    )
    val phase2 by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(IslandMotion.waveformPeriodMs + 180), RepeatMode.Reverse),
        label = "wave2",
    )
    val phase3 by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(IslandMotion.waveformPeriodMs + 90), RepeatMode.Reverse),
        label = "wave3",
    )
    val factors = if (!playing || reduceMotion) listOf(0.45f, 0.85f, 0.6f) else listOf(phase1, phase2, phase3)

    Row(
        modifier = modifier.height(barHeight),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(barWidth * 0.7f),
    ) {
        factors.forEach { factor ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(factor.coerceIn(0.2f, 1f))
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(color),
            )
        }
    }
}

/** Dots for the multi-event stack: one per active event, the focused one is wider. */
@Composable
fun IslandQueueDots(
    count: Int,
    focusedIndex: Int,
    modifier: Modifier = Modifier,
    color: Color = IslandColors.TextPrimary,
    inactiveColor: Color = IslandColors.TextTertiary,
) {
    if (count <= 1) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IslandSpacing.xxs),
    ) {
        repeat(count.coerceAtMost(6)) { index ->
            val focused = index == focusedIndex
            Box(
                modifier = Modifier
                    .width(if (focused) IslandDimensions.queueDotSize * 2.4f else IslandDimensions.queueDotSize)
                    .height(IslandDimensions.queueDotSize)
                    .clip(CircleShape)
                    .background(if (focused) color else inactiveColor),
            )
        }
    }
}

/** Event action buttons. Only actions the platform actually supports are ever in the list. */
@Composable
fun IslandActionRow(
    actions: List<IslandAction>,
    modifier: Modifier = Modifier,
    accent: Color = IslandColors.AccentDefault,
    compact: Boolean = false,
    onAction: (IslandAction) -> Unit = LocalIslandRender.current.onAction,
) {
    if (actions.isEmpty()) return
    val size = if (compact) IslandDimensions.controlSizeCompact else IslandDimensions.controlSize
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IslandSpacing.sm, Alignment.CenterHorizontally),
    ) {
        actions.take(4).forEach { action ->
            IslandActionButton(action = action, size = size, accent = accent, onAction = onAction)
        }
    }
}

@Composable
fun IslandActionButton(
    action: IslandAction,
    modifier: Modifier = Modifier,
    size: Dp = IslandDimensions.controlSize,
    accent: Color = IslandColors.AccentDefault,
    onAction: (IslandAction) -> Unit = LocalIslandRender.current.onAction,
) {
    val label = actionLabel(action)
    val isDismiss = action.kind == IslandActionKind.DISMISS
    val tint = when {
        !action.enabled -> IslandColors.TextTertiary
        isDismiss -> IslandColors.TextSecondary
        else -> accent
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isDismiss) IslandColors.ScrimLight else Color.Transparent)
            .clickable(
                enabled = action.enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onAction(action) },
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = actionIconFor(action),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

/** One text line that never wraps and never pushes the pill wider than its bounds. */
@Composable
fun IslandText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = IslandColors.TextPrimary,
    style: TextStyle = IslandTypography.collapsedTitle,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
        softWrap = false,
        modifier = modifier,
    )
}

/** Painter for framework Drawables (app icons) without an extra dependency. */
@Composable
fun rememberDrawablePainter(drawable: Drawable): Painter = remember(drawable) { DrawablePainter(drawable) }

private class DrawablePainter(private val drawable: Drawable) : Painter() {

    private var currentAlpha: Float = 1f

    override val intrinsicSize: Size
        get() {
            val width = drawable.intrinsicWidth
            val height = drawable.intrinsicHeight
            return if (width <= 0 || height <= 0) Size.Unspecified else Size(width.toFloat(), height.toFloat())
        }

    override fun applyAlpha(alpha: Float): Boolean {
        currentAlpha = alpha
        return true
    }

    override fun DrawScope.onDraw() {
        val width = size.width.toInt().coerceAtLeast(1)
        val height = size.height.toInt().coerceAtLeast(1)
        drawable.setBounds(0, 0, width, height)
        drawIntoCanvas { canvas ->
            val previous = drawable.alpha
            drawable.alpha = (currentAlpha * 255).toInt().coerceIn(0, 255)
            drawable.draw(canvas.nativeCanvas)
            drawable.alpha = previous
        }
    }
}

/** px ↔ dp helper so renderers never hard-code pixel values. */
@Composable
fun rememberDensityScale(): Float = LocalDensity.current.density
