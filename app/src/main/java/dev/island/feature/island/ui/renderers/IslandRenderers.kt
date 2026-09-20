package dev.island.feature.island.ui.renderers

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import dev.island.domain.model.IslandAction
import dev.island.domain.model.IslandAppearance
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandSettings

/** How much of the event the renderer should draw. */
enum class IslandRenderMode {
    /** Compact pill: icon + one line + optional progress. */
    COLLAPSED,

    /** Full card: event-specific layout with controls. */
    EXPANDED,

    /** Landscape "minimize": a tiny indicator. */
    MINIMIZED,
}

/**
 * Every event family implements this, which is how new features are added without touching the
 * shell: implement [canRender], draw the modes, register in [IslandRendererRegistry].
 */
interface IslandRenderer {
    /** Lower runs first; the generic renderer is last. */
    val order: Int

    fun canRender(event: IslandEvent): Boolean

    @Composable
    fun Render(event: IslandEvent, mode: IslandRenderMode, modifier: Modifier)
}

/**
 * Everything a renderer may need, provided through a composition local so renderers stay small,
 * independently previewable and free of parameter drilling.
 */
@Immutable
data class IslandRenderContext(
    val settings: IslandSettings = IslandSettings.Default,
    val appearance: IslandAppearance = IslandAppearance(),
    val reduceMotion: Boolean = false,
    val animationSpeed: Float = 1f,
    /** Monotonic "now", refreshed while the island is visible. Renderers must not poll. */
    val nowElapsedMs: Long = 0L,
    val queueCount: Int = 1,
    val queueIndex: Int = 0,
    val onAction: (IslandAction) -> Unit = {},
    val artwork: (String?) -> Bitmap? = { null },
    val appIcon: (String?) -> Drawable? = { null },
)

val LocalIslandRender = compositionLocalOf { IslandRenderContext() }

/** Chooses the renderer for an event. The last entry must always accept anything. */
class IslandRendererRegistry(renderers: List<IslandRenderer>) {

    private val ordered = renderers.sortedBy { it.order }

    fun rendererFor(event: IslandEvent): IslandRenderer =
        ordered.firstOrNull { it.canRender(event) } ?: ordered.last()
}
