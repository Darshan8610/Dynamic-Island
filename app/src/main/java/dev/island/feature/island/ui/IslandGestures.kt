package dev.island.feature.island.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class SwipeDirection { LEFT, RIGHT, UP, DOWN }

/** Everything the shell can be asked to do by a finger. Business logic stays out of Composables. */
data class IslandGestureCallbacks(
    val onTap: () -> Unit = {},
    val onDoubleTap: () -> Unit = {},
    val onLongPress: () -> Unit = {},
    val onSwipe: (SwipeDirection) -> Unit = {},
    val onDragStart: () -> Unit = {},
    val onDrag: (deltaXPx: Float, deltaYPx: Float) -> Unit = { _, _ -> },
    val onDragEnd: () -> Unit = {},
    val enabled: Boolean = true,
)

/**
 * Island gestures.
 *
 * The overlay window is exactly the size of the island, so every touch reaching this modifier is
 * already inside the interactive region — touches anywhere else go to the app underneath, because
 * the window is `FLAG_NOT_TOUCH_MODAL` and never full screen. System edge gestures are untouched:
 * the island is inset from every screen edge, and drag detection starts only after touch slop.
 *
 * Taps use [detectTapGestures]; swipes use foundation's [draggable], which coexists with tap
 * detection and gives correct velocity/slop behaviour for free.
 */
@Composable
fun rememberIslandGestures(
    callbacks: IslandGestureCallbacks,
    swipeThreshold: Dp = 28.dp,
): Modifier {
    val density = LocalDensity.current
    val thresholdPx = with(density) { swipeThreshold.toPx() }
    var horizontalTotal by remember { mutableFloatStateOf(0f) }
    var verticalTotal by remember { mutableFloatStateOf(0f) }

    val stableCallbacks = remember(
        callbacks.onTap,
        callbacks.onDoubleTap,
        callbacks.onLongPress,
        callbacks.onSwipe,
        callbacks.onDragStart,
        callbacks.onDrag,
        callbacks.onDragEnd,
        callbacks.enabled,
    ) { callbacks }

    val horizontalState = rememberDraggableState { delta ->
        horizontalTotal += delta
        stableCallbacks.onDrag(delta, 0f)
    }
    val verticalState = rememberDraggableState { delta ->
        verticalTotal += delta
        stableCallbacks.onDrag(0f, delta)
    }

    return Modifier
        .then(
            if (!stableCallbacks.enabled) {
                Modifier
            } else {
                Modifier
                    .pointerInput(stableCallbacks) {
                        detectTapGestures(
                            onTap = { stableCallbacks.onTap() },
                            onDoubleTap = { stableCallbacks.onDoubleTap() },
                            onLongPress = { stableCallbacks.onLongPress() },
                        )
                    }
                    .draggable(
                        state = horizontalState,
                        orientation = Orientation.Horizontal,
                        onDragStarted = {
                            horizontalTotal = 0f
                            stableCallbacks.onDragStart()
                        },
                        onDragStopped = {
                            val total = horizontalTotal
                            horizontalTotal = 0f
                            when {
                                total > thresholdPx -> stableCallbacks.onSwipe(SwipeDirection.RIGHT)
                                total < -thresholdPx -> stableCallbacks.onSwipe(SwipeDirection.LEFT)
                            }
                            stableCallbacks.onDragEnd()
                        },
                    )
                    .draggable(
                        state = verticalState,
                        orientation = Orientation.Vertical,
                        onDragStarted = {
                            verticalTotal = 0f
                            stableCallbacks.onDragStart()
                        },
                        onDragStopped = {
                            val total = verticalTotal
                            verticalTotal = 0f
                            when {
                                total < -thresholdPx -> stableCallbacks.onSwipe(SwipeDirection.UP)
                                total > thresholdPx -> stableCallbacks.onSwipe(SwipeDirection.DOWN)
                            }
                            stableCallbacks.onDragEnd()
                        },
                    )
            },
        )
}
