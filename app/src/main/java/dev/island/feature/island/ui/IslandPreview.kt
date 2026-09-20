package dev.island.feature.island.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.island.core.platform.CutoutKind
import dev.island.core.ui.theme.IslandElevation
import dev.island.core.ui.theme.islandSurfaceColor
import dev.island.domain.model.IslandAction
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandPhase
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.IslandUiState
import dev.island.feature.island.layout.IslandLayoutInput
import dev.island.feature.island.layout.IslandMetrics
import dev.island.feature.island.ui.renderers.IslandRenderContext
import dev.island.feature.island.ui.renderers.IslandRenderMode
import dev.island.feature.island.ui.renderers.IslandRendererRegistry
import kotlinx.coroutines.delay

/**
 * In-app island preview.
 *
 * This is not a mock-up: it renders the real event through the real [IslandRendererRegistry] inside
 * the real [IslandContainer], using the same [IslandMetrics] geometry the overlay uses — only the
 * layout input is synthetic (a generic centred punch-hole phone). What the user sees in Settings →
 * Customize is therefore what the overlay draws, and a change to a renderer shows up in both places
 * at once.
 */
@Composable
fun IslandPreview(
    event: IslandEvent,
    settings: IslandSettings,
    registry: IslandRendererRegistry,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    screenWidthDp: Dp = 400.dp,
    onAction: (IslandAction) -> Unit = {},
    artworkProvider: (String?) -> android.graphics.Bitmap? = { null },
    appIconProvider: (String?) -> android.graphics.drawable.Drawable? = { null },
) {
    val appearance = settings.appearance
    val reduceMotion = settings.behavior.reduceMotion

    val layoutInput = remember(settings, screenWidthDp) {
        IslandLayoutInput(
            screenWidthDp = screenWidthDp.value,
            screenHeightDp = 860f,
            safeTopDp = 28f,
            statusBarHeightDp = 24f,
            cutoutKind = CutoutKind.CENTER_PUNCH_HOLE,
            hasCutout = true,
            cutoutTopDp = 8f,
            cutoutBottomDp = 32f,
            cutoutLeftDp = screenWidthDp.value / 2f - 8f,
            cutoutRightDp = screenWidthDp.value / 2f + 8f,
            landscape = false,
            isLargeScreen = false,
            fontScale = 1f,
            position = settings.behavior.position,
            customVerticalOffsetDp = settings.behavior.customVerticalOffsetDp,
            landscapeBehavior = settings.behavior.landscapeBehavior,
            appearance = appearance,
        )
    }

    val bounds = if (expanded) {
        IslandMetrics.expanded(layoutInput, event.type)
    } else {
        IslandMetrics.collapsed(layoutInput)
    }

    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(event.id) {
        while (true) {
            delay(PREVIEW_TICK_MS)
            now = SystemClock.elapsedRealtime()
        }
    }

    val uiState = remember(event) {
        IslandUiState(
            phase = if (expanded) IslandPhase.EXPANDED else IslandPhase.COLLAPSED,
            events = listOf(event),
            focusedEventId = event.id,
            expanded = expanded,
            enabled = true,
            disabledReason = null,
        )
    }
    val renderContext = remember(settings, appearance, reduceMotion, now, onAction, artworkProvider, appIconProvider) {
        IslandRenderContext(
            settings = settings,
            appearance = appearance,
            reduceMotion = reduceMotion,
            animationSpeed = appearance.animationSpeed,
            nowElapsedMs = now,
            queueCount = 1,
            queueIndex = 0,
            onAction = onAction,
            artwork = artworkProvider,
            appIcon = appIconProvider,
        )
    }

    Box(
        modifier = modifier.size(width = bounds.widthDp.dp, height = bounds.heightDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        IslandContainer(
            visible = true,
            cornerRadius = bounds.cornerRadiusDp.dp,
            shape = RoundedCornerShape(bounds.cornerRadiusDp.dp),
            surfaceColor = islandSurfaceColor(appearance.themeMode, appearance.opacity),
            elevation = if (expanded) IslandElevation.islandExpanded else IslandElevation.island,
            animationSpeed = appearance.animationSpeed,
            reduceMotion = reduceMotion,
            modifier = Modifier.size(width = bounds.widthDp.dp, height = bounds.heightDp.dp),
        ) {
            IslandContent(
                uiState = uiState,
                registry = registry,
                mode = if (expanded) IslandRenderMode.EXPANDED else IslandRenderMode.COLLAPSED,
                renderContext = renderContext,
                animationSpeed = appearance.animationSpeed,
                reduceMotion = reduceMotion,
            )
        }
    }
}

private const val PREVIEW_TICK_MS = 500L
