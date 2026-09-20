package dev.island.feature.island.ui

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.island.R
import dev.island.core.animation.IslandAnimation
import dev.island.core.platform.CutoutInfo
import dev.island.core.platform.DisplayInfo
import dev.island.core.ui.theme.IslandElevation
import dev.island.core.ui.theme.IslandMotion
import dev.island.core.ui.theme.islandSurfaceColor
import dev.island.domain.model.CallState
import dev.island.domain.model.IslandAction
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.IslandUiState
import dev.island.domain.model.PlaybackState
import dev.island.feature.island.layout.IslandBounds
import dev.island.feature.island.layout.IslandLayoutInput
import dev.island.feature.island.layout.IslandMetrics
import dev.island.feature.island.ui.renderers.IslandRenderContext
import dev.island.feature.island.ui.renderers.IslandRenderMode
import dev.island.feature.island.ui.renderers.IslandRendererRegistry
import kotlinx.coroutines.delay

/**
 * Root of the overlay's Compose tree — the only place that knows how the island is positioned,
 * sized and animated. Everything below it is pure content.
 *
 * Responsibilities, and deliberately nothing else:
 *
 * 1. Turn live insets + display geometry + settings into an [IslandLayoutInput] and ask
 *    [IslandMetrics] for [IslandBounds]. No dimension is ever hard-coded here.
 * 2. Report those bounds upward so the window can be resized/moved by [IslandWindowManager].
 *    The window is resized *before* content animates, so the surface is never clipped.
 * 3. Drive one shared ticker for time-based content (media position, timers, stopwatch, call
 *    duration). The ticker runs only while the island is visible AND an event actually needs it,
 *    which is the difference between an overlay that idles at 0% CPU and one that never sleeps.
 * 4. Provide [IslandRenderContext] and hand gestures straight through to the caller.
 *
 * Drag feedback is a rubber-band transform of the surface, not a window move: moving a
 * `TYPE_APPLICATION_OVERLAY` window every frame costs a layout pass per frame, and the swipe
 * decision only needs the total delta, which [rememberIslandGestures] already tracks.
 */
@Composable
fun IslandOverlayRoot(
    uiState: IslandUiState,
    settings: IslandSettings,
    cutout: CutoutInfo,
    display: DisplayInfo,
    registry: IslandRendererRegistry,
    systemReduceMotion: Boolean,
    artworkProvider: (String?) -> Bitmap?,
    appIconProvider: (String?) -> Drawable?,
    gestures: IslandGestureCallbacks,
    onAction: (IslandAction) -> Unit,
    onBoundsChanged: (IslandBounds) -> Unit,
    onAnimationSettled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = settings.appearance
    val behavior = settings.behavior
    val reduceMotion = behavior.reduceMotion || systemReduceMotion
    val animationSpeed = appearance.animationSpeed

    val focused = uiState.focusedEvent
    val hasContent = uiState.isRendering && focused != null
    val expanded = hasContent && uiState.expanded && focused?.expandable != false

    val layoutInput = rememberLayoutInput(
        settings = settings,
        cutout = cutout,
        display = display,
    )

    val measured = if (expanded) {
        IslandMetrics.expanded(layoutInput, focused?.type)
    } else {
        IslandMetrics.collapsed(layoutInput)
    }
    val bounds = if (hasContent) measured else measured.copy(visible = false)

    // Window geometry first, content second: the window is already the right size when the
    // surface starts animating, so nothing is clipped mid-transition.
    LaunchedEffect(bounds) { onBoundsChanged(bounds) }

    val mode = when {
        bounds.minimized -> IslandRenderMode.MINIMIZED
        expanded -> IslandRenderMode.EXPANDED
        else -> IslandRenderMode.COLLAPSED
    }

    val tickIntervalMs = remember(focused, hasContent) { tickIntervalFor(focused, hasContent) }
    val nowElapsedMs = rememberIslandTicker(tickIntervalMs)

    // Finger-following feedback, damped and clamped so it reads as resistance, not displacement.
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val targetDragXPx = remember(dragX, density) {
        with(density) { (dragX / DRAG_DAMPING).toDp().coerceIn(-28.dp, 28.dp).toPx() }
    }
    val targetDragYPx = remember(dragY, density) {
        with(density) { (dragY / DRAG_DAMPING).toDp().coerceIn(-20.dp, 20.dp).toPx() }
    }
    val dampedDragX by animateFloatAsState(
        targetValue = targetDragXPx,
        animationSpec = IslandAnimation.crossfade(animationSpeed, reduceMotion),
        label = "island-drag-x",
    )
    val dampedDragY by animateFloatAsState(
        targetValue = targetDragYPx,
        animationSpec = IslandAnimation.crossfade(animationSpeed, reduceMotion),
        label = "island-drag-y",
    )

    val gestureCallbacks = remember(gestures) {
        gestures.copy(
            onDrag = { deltaX, deltaY ->
                dragX += deltaX
                dragY += deltaY
                gestures.onDrag(deltaX, deltaY)
            },
            onDragEnd = {
                dragX = 0f
                dragY = 0f
                gestures.onDragEnd()
            },
        )
    }

    val renderContext = remember(
        settings,
        appearance,
        reduceMotion,
        animationSpeed,
        nowElapsedMs,
        uiState.activeEvents.size,
        uiState.focusedEventId,
        focused,
        artworkProvider,
        appIconProvider,
        onAction,
        uiState.activeEvents,
        uiState.focusedEventId,
    ) {
        IslandRenderContext(
            settings = settings,
            appearance = appearance,
            reduceMotion = reduceMotion,
            animationSpeed = animationSpeed,
            nowElapsedMs = nowElapsedMs,
            queueCount = uiState.activeEvents.size,
            queueIndex = uiState.activeEvents.indexOfFirst { it.id == uiState.focusedEventId }.coerceAtLeast(0),
            onAction = onAction,
            artwork = artworkProvider,
            appIcon = appIconProvider,
        )
    }

    val stateLabel = stringResource(if (expanded) R.string.a11y_island_expanded else R.string.a11y_island_collapsed)
    val contentLabel = focused?.let { "${it.title}${it.subtitle?.let { sub -> ", $sub" } ?: ""}" } ?: ""

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) {
                contentDescription = if (contentLabel.isBlank()) stateLabel else "$stateLabel, $contentLabel"
                stateDescription = stateLabel
                // TalkBack announces event changes without the island stealing focus.
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        IslandContainer(
            visible = bounds.visible,
            cornerRadius = bounds.cornerRadiusDp.dp,
            shape = RoundedCornerShape(bounds.cornerRadiusDp.dp),
            surfaceColor = islandSurfaceColor(appearance.themeMode, appearance.opacity),
            elevation = if (expanded) IslandElevation.islandExpanded else IslandElevation.island,
            animationSpeed = animationSpeed,
            reduceMotion = reduceMotion,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = dampedDragX
                    translationY = dampedDragY
                }
                .then(rememberIslandGestures(gestureCallbacks)),
        ) {
            IslandContent(
                uiState = uiState,
                registry = registry,
                mode = mode,
                renderContext = renderContext,
                animationSpeed = animationSpeed,
                reduceMotion = reduceMotion,
            )
        }
    }

    // Tell the engine the transition finished so it can run its post-animation bookkeeping
    // (collapse timers, phase settling). The engine also has its own fallback timeout.
    LaunchedEffect(mode, bounds.visible, uiState.focusGeneration) {
        val settleMs = IslandAnimation.durationMs(
            if (expanded) IslandMotion.expandDurationMs else IslandMotion.collapseDurationMs,
            animationSpeed,
        ) + ANIMATION_SETTLE_SLACK_MS
        delay(settleMs.toLong())
        onAnimationSettled()
    }
}

private const val ANIMATION_SETTLE_SLACK_MS = 80
private const val DRAG_DAMPING = 3f

/**
 * Builds the layout input from live values. Cutout geometry is converted from px to dp with the
 * display's own density, so the same code is correct on a 2.0x phone, a 3.5x flagship, a tablet
 * and a foldable in either posture.
 */
@Composable
private fun rememberLayoutInput(
    settings: IslandSettings,
    cutout: CutoutInfo,
    display: DisplayInfo,
): IslandLayoutInput {
    val behavior = settings.behavior
    return remember(settings, cutout, display) {
        val density = display.density.takeIf { it > 0f } ?: 1f
        val cutoutLeftPx = cutout.boundsPx.firstOrNull()?.left
            ?: cutout.cutoutCenterXPx?.let { it - cutout.cutoutWidthPx / 2 }
            ?: 0
        val cutoutRightPx = cutout.boundsPx.firstOrNull()?.right
            ?: cutout.cutoutCenterXPx?.let { it + cutout.cutoutWidthPx / 2 }
            ?: 0

        IslandLayoutInput(
            screenWidthDp = display.widthDp,
            screenHeightDp = display.heightDp,
            safeTopDp = cutout.safeTopPx / density,
            statusBarHeightDp = cutout.statusBarHeightPx / density,
            cutoutKind = cutout.kind,
            hasCutout = cutout.hasCutout,
            cutoutTopDp = cutout.cutoutTopPx / density,
            cutoutBottomDp = cutout.cutoutBottomPx / density,
            cutoutLeftDp = cutoutLeftPx / density,
            cutoutRightDp = cutoutRightPx / density,
            landscape = display.landscape,
            isLargeScreen = display.isLargeScreen,
            fontScale = display.fontScale,
            position = behavior.position,
            customVerticalOffsetDp = behavior.customVerticalOffsetDp,
            landscapeBehavior = behavior.landscapeBehavior,
            appearance = settings.appearance,
        )
    }
}

/**
 * One ticker for the whole island. Returns 0 when nothing needs time, which stops the loop
 * entirely — a paused island costs no frames and no wakeups.
 */
@Composable
private fun rememberIslandTicker(intervalMs: Long): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(intervalMs) {
        if (intervalMs <= 0L) return@LaunchedEffect
        while (true) {
            delay(intervalMs)
            now = SystemClock.elapsedRealtime()
        }
    }
    return now
}

/**
 * Tick rate per event family. A running stopwatch needs tenths; a charging pill does not need to
 * move at all. Returning 0 disables the ticker.
 */
private fun tickIntervalFor(event: IslandEvent?, hasContent: Boolean): Long {
    if (!hasContent || event == null) return 0L
    return when (event) {
        is IslandEvent.Stopwatch -> if (event.stopwatch.running) STOPWATCH_TICK_MS else 0L
        is IslandEvent.Timer -> if (event.timer.running || event.timer.finished) SECOND_TICK_MS else 0L
        is IslandEvent.Call -> when (event.call.state) {
            CallState.ACTIVE, CallState.HOLDING -> SECOND_TICK_MS
            else -> 0L
        }

        is IslandEvent.Media -> when (event.media.state) {
            PlaybackState.PLAYING, PlaybackState.BUFFERING -> MEDIA_TICK_MS
            else -> 0L
        }

        is IslandEvent.Download -> if (event.download.completed) 0L else SECOND_TICK_MS
        is IslandEvent.Navigation -> SECOND_TICK_MS
        is IslandEvent.Alarm -> if (event.alarm.isRinging) 0L else MINUTE_TICK_MS
        is IslandEvent.Charging, is IslandEvent.Battery, is IslandEvent.Bluetooth -> 0L
        is IslandEvent.Notification ->
            if (event.notification.progressIndeterminate || event.notification.hasProgress) SECOND_TICK_MS else 0L

        is IslandEvent.System, is IslandEvent.Custom -> if (event.progress != null) SECOND_TICK_MS else 0L
    }
}

private const val MEDIA_TICK_MS = 500L
private const val SECOND_TICK_MS = 1_000L
private const val STOPWATCH_TICK_MS = 100L
private const val MINUTE_TICK_MS = 15_000L
