package dev.island.feature.island.ui.renderers

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.island.R
import dev.island.core.ui.theme.IslandColors
import dev.island.core.ui.theme.IslandDimensions
import dev.island.core.ui.theme.IslandTypography
import dev.island.core.ui.theme.resolveAccent
import dev.island.domain.model.IslandAction
import dev.island.domain.model.IslandActionKind
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandIconKey
import dev.island.domain.model.MediaInfo
import dev.island.domain.model.PlaybackState
import dev.island.feature.island.ui.widgets.IslandActionRow
import dev.island.feature.island.ui.widgets.IslandArtwork
import dev.island.feature.island.ui.widgets.IslandCollapsedRow
import dev.island.feature.island.ui.widgets.IslandExpandedCard
import dev.island.feature.island.ui.widgets.IslandFormat
import dev.island.feature.island.ui.widgets.IslandHeader
import dev.island.feature.island.ui.widgets.IslandIcon
import dev.island.feature.island.ui.widgets.IslandProgress
import dev.island.feature.island.ui.widgets.IslandQueueStrip
import dev.island.feature.island.ui.widgets.IslandText
import dev.island.feature.island.ui.widgets.PlayingIndicator

/**
 * Media island — the signature case.
 *
 * Position is *derived*, never polled: [MediaInfo.positionAt] projects from the monotonic sample
 * the media session gave us, and the render context supplies a single shared ticker, so a playing
 * track costs one recomposition per tick for the whole island instead of one per widget.
 *
 * Collapsed keeps art + title + waveform (+ a hairline progress bar). Expanded adds the seek bar
 * and transport controls, both honouring the user's media settings and the session's capabilities —
 * a control the session cannot execute is never drawn.
 */
class MediaIslandRenderer : IslandRenderer {

    override val order: Int = 10

    override fun canRender(event: IslandEvent): Boolean = event is IslandEvent.Media

    @Composable
    override fun Render(event: IslandEvent, mode: IslandRenderMode, modifier: Modifier) {
        val mediaEvent = event as? IslandEvent.Media ?: return
        val context = LocalIslandRender.current
        val media = mediaEvent.media
        val settings = context.settings.media
        val accent = resolveAccent(context.appearance.accentArgb, event.accentArgb)
        val playing = media.state == PlaybackState.PLAYING

        when (mode) {
            IslandRenderMode.MINIMIZED -> {
                val minimizedLabel =
                    stringResource(if (playing) R.string.a11y_playing else R.string.a11y_paused)
                Box(
                    modifier = modifier.semantics { contentDescription = minimizedLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    IslandIcon(
                        iconKey = IslandIconKey.MUSIC,
                        contentDescription = null,
                        tint = accent,
                        size = IslandDimensions.iconSize,
                    )
                }
            }

            IslandRenderMode.COLLAPSED -> IslandCollapsedRow(
                modifier = modifier,
                leading = {
                    if (settings.showAlbumArt) {
                        IslandArtwork(token = media.artworkToken, size = IslandDimensions.albumArtCollapsed)
                    } else {
                        IslandIcon(
                            iconKey = IslandIconKey.MUSIC,
                            contentDescription = null,
                            tint = accent,
                        )
                    }
                },
                title = media.title,
                trailing = {
                    if (settings.showCollapsedWaveform) {
                        PlayingIndicator(
                            playing = playing,
                            color = accent,
                            reduceMotion = context.reduceMotion,
                        )
                    } else {
                        IslandText(
                            text = IslandFormat.clock(media.positionAt(context.nowElapsedMs)),
                            style = IslandTypography.caption,
                            color = IslandColors.TextSecondary,
                        )
                    }
                },
                progress = if (settings.showProgressBar) media.fractionAt(context.nowElapsedMs) else null,
                progressColor = accent,
            )

            IslandRenderMode.EXPANDED -> MediaExpanded(
                event = mediaEvent,
                media = media,
                accent = accent,
                modifier = modifier,
            )
        }
    }

    @Composable
    private fun MediaExpanded(
        event: IslandEvent.Media,
        media: MediaInfo,
        accent: Color,
        modifier: Modifier,
    ) {
        val context = LocalIslandRender.current
        val settings = context.settings.media
        val position = media.positionAt(context.nowElapsedMs)
        val duration = media.durationMs
        val fraction = media.fractionAt(context.nowElapsedMs)

        IslandExpandedCard(
            modifier = modifier,
            header = {
                IslandHeader(
                    title = media.title,
                    subtitle = media.artist ?: media.album ?: media.sourceLabel,
                    leading = {
                        IslandArtwork(token = media.artworkToken, size = IslandDimensions.albumArtExpanded)
                    },
                    trailing = {
                        IslandQueueStrip(count = context.queueCount, focusedIndex = context.queueIndex)
                    },
                )
            },
            body = {
                if (settings.showProgressBar) {
                    MediaSeekBar(
                        fraction = fraction,
                        seekEnabled = settings.allowSeek && media.canSeek && duration != null && duration > 0L,
                        accent = accent,
                        onSeekFraction = { target ->
                            val targetMs = ((duration ?: 0L) * target).toLong()
                            context.onAction(
                                IslandAction(
                                    id = "media-seek",
                                    kind = IslandActionKind.SEEK,
                                    iconKey = IslandIconKey.MUSIC,
                                    seekToMs = targetMs,
                                ),
                            )
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = IslandFormat.clock(position),
                            color = IslandColors.TextTertiary,
                            style = IslandTypography.caption,
                        )
                        Text(
                            text = duration?.let { IslandFormat.clock(it) } ?: "—",
                            color = IslandColors.TextTertiary,
                            style = IslandTypography.caption,
                        )
                    }
                }
            },
            controls = {
                if (settings.showTransportControls) {
                    IslandActionRow(actions = event.actions, accent = accent)
                }
            },
        )
    }
}

/** Determinate bar that also seeks on tap when the session allows it. */
@Composable
private fun MediaSeekBar(
    fraction: Float?,
    seekEnabled: Boolean,
    accent: Color,
    onSeekFraction: (Float) -> Unit,
) {
    val progressDescription = stringResource(
        R.string.a11y_progress,
        ((fraction ?: 0f) * 100).toInt(),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = progressDescription }
            .then(
                if (seekEnabled) {
                    Modifier.pointerInput(onSeekFraction) {
                        detectTapGestures { offset ->
                            val target = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onSeekFraction(target)
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        IslandProgress(
            progress = fraction,
            modifier = Modifier.fillMaxWidth(),
            height = IslandDimensions.progressHeightExpanded,
            color = accent,
        )
    }
}
