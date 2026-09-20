package dev.island.feature.island.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import dev.island.core.animation.IslandAnimation
import dev.island.core.ui.theme.IslandMotion
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandUiState
import dev.island.feature.island.ui.renderers.IslandRenderContext
import dev.island.feature.island.ui.renderers.IslandRenderMode
import dev.island.feature.island.ui.renderers.IslandRendererRegistry
import dev.island.feature.island.ui.renderers.LocalIslandRender

/**
 * The island's physical surface: the single black, rounded shape that every event lives inside.
 *
 * It owns exactly three visual properties — alpha, corner radius and elevation — and knows nothing
 * about events. Content is a slot, so the same surface serves collapsed, expanded and transient
 * states. Visibility is animated here ([animateFloatAsState]), never by toggling the view, and the
 * surface stays composed while invisible so no recomposition storm happens on show/hide.
 */
@Composable
fun IslandContainer(
    visible: Boolean,
    cornerRadius: Dp,
    shape: Shape,
    surfaceColor: Color,
    elevation: Dp,
    animationSpeed: Float,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = IslandAnimation.fade(animationSpeed, reduceMotion),
        label = "island-surface-alpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow(elevation = elevation, shape = shape, clip = false)
            .clip(shape)
            .background(surfaceColor)
            // One alpha for the whole layer, so text, icons and surface fade together.
            .graphicsLayer { this.alpha = alpha },
        content = content,
    )
}

/** Single key describing what the island is showing; deliberately excludes volatile fields. */
private data class IslandContentKey(val eventId: String?, val mode: IslandRenderMode)

/**
 * Chooses and renders the right [dev.island.feature.island.ui.renderers.IslandRenderer] for the
 * focused event, crossfading when the focused event or the render mode changes.
 *
 * Volatile fields (progress, remaining time) are deliberately NOT part of the animation key —
 * otherwise every tick would restart the transition. They arrive through the render context and
 * simply recompose the content in place.
 */
@Composable
fun IslandContent(
    uiState: IslandUiState,
    registry: IslandRendererRegistry,
    mode: IslandRenderMode,
    renderContext: IslandRenderContext,
    animationSpeed: Float,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val focused = uiState.focusedEvent

    // Last known instance per event id, so an exiting event still has content to draw while the
    // crossfade runs (the engine may already have removed it from the queue).
    val eventCache = remember { mutableStateMapOf<String, IslandEvent>() }
    SideEffect {
        if (focused != null) eventCache[focused.id] = focused
        if (eventCache.size > 12) {
            val activeIds = uiState.activeEvents.map { it.id }.toSet()
            eventCache.keys.filterNot { it in activeIds || it == focused?.id }.take(eventCache.size - 12)
                .forEach { eventCache.remove(it) }
        }
    }

    val key = IslandContentKey(focused?.id, mode)

    CompositionLocalProvider(LocalIslandRender provides renderContext) {
        AnimatedContent(
            targetState = key,
            modifier = modifier.fillMaxSize(),
            transitionSpec = {
                val crossfadeMs = IslandAnimation.durationMs(IslandMotion.contentCrossfadeMs, animationSpeed)
                fadeIn(tween(crossfadeMs)) togetherWith fadeOut(tween((crossfadeMs * 0.6f).toInt()))
            },
            label = "island-content",
        ) { contentKey ->
            val event = contentKey.eventId?.let { eventCache[it] }
            if (event == null) {
                Box(Modifier.fillMaxSize())
            } else {
                val renderer = registry.rendererFor(event)
                renderer.Render(event = event, mode = contentKey.mode, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
