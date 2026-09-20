package dev.island.feature.island.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.island.core.ui.theme.IslandColors
import dev.island.core.ui.theme.IslandCorners
import dev.island.core.ui.theme.IslandDimensions
import dev.island.core.ui.theme.IslandSpacing
import dev.island.core.ui.theme.IslandTypography

/**
 * Layout shells shared by every renderer so all event types breathe the same way:
 *
 * - [IslandCollapsedRow] → the 32–42 dp pill: leading slot, one title line, optional trailing slot.
 * - [IslandExpandedCard] → the event-specific card: header, body, controls, footer.
 * - [IslandMetric]       → the big number (timer, stopwatch, battery).
 *
 * Renderers supply content only; sizing, spacing and rhythm come from the design tokens.
 */

@Composable
fun IslandCollapsedRow(
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    title: String,
    titleStyle: TextStyle = IslandTypography.collapsedTitle,
    titleColor: Color = IslandColors.TextPrimary,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    progress: Float? = null,
    progressColor: Color = IslandColors.AccentDefault,
    indeterminateProgress: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = IslandSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IslandSpacing.sm),
        ) {
            if (leading != null) {
                Box(
                    modifier = Modifier.size(IslandDimensions.iconSizeLarge),
                    contentAlignment = Alignment.Center,
                ) {
                    leading()
                }
            }
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    color = titleColor,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = IslandColors.TextSecondary,
                        style = IslandTypography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(IslandSpacing.xs))
                trailing()
            }
        }

        if (progress != null || indeterminateProgress) {
            IslandProgress(
                progress = if (indeterminateProgress) null else progress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = IslandSpacing.md),
                height = IslandDimensions.progressHeight,
                color = progressColor,
            )
        }
    }
}

/**
 * Expanded card scaffold. The card surface is drawn by
 * [dev.island.feature.island.ui.IslandContainer]; this only arranges header/body/controls.
 */
@Composable
fun IslandExpandedCard(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    body: (@Composable ColumnScope.() -> Unit)? = null,
    controls: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    contentPadding: Dp = IslandSpacing.lg,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(IslandSpacing.md),
    ) {
        header()
        if (body != null) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(IslandSpacing.sm),
                content = body,
            )
        }
        if (controls != null) controls()
        if (footer != null) footer()
    }
}

/** Header row: leading artwork/icon + title + subtitle + optional trailing slot. */
@Composable
fun IslandHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleStyle: TextStyle = IslandTypography.expandedTitle,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IslandSpacing.md),
    ) {
        if (leading != null) leading()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(IslandSpacing.xxxs),
        ) {
            Text(
                text = title,
                color = IslandColors.TextPrimary,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = IslandColors.TextSecondary,
                    style = IslandTypography.expandedSubtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/** Big metric (timer, stopwatch, battery). Tabular figures keep digits from jittering. */
@Composable
fun IslandMetric(
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    color: Color = IslandColors.TextPrimary,
    style: TextStyle = IslandTypography.timerLarge,
    align: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = align,
        verticalArrangement = Arrangement.spacedBy(IslandSpacing.xxs),
    ) {
        Text(
            text = value,
            color = color,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
        if (caption != null) {
            Text(
                text = caption,
                color = IslandColors.TextSecondary,
                style = IslandTypography.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Small uppercase section label used inside expanded cards and stacked events. */
@Composable
fun IslandSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = IslandColors.TextTertiary,
) {
    Text(
        text = text.uppercase(),
        color = color,
        style = IslandTypography.label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Rounded chip ("64%", "23W", "LAP 3"). */
@Composable
fun IslandChip(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = IslandColors.ScrimLight,
    contentColor: Color = IslandColors.TextPrimary,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(IslandCorners.chip))
            .background(background)
            .padding(horizontal = IslandSpacing.sm, vertical = IslandSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = IslandTypography.caption,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Hairline used between stacked events (music + timer). */
@Composable
fun IslandDivider(modifier: Modifier = Modifier, color: Color = IslandColors.HairlineLight) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

/** Compact queue indicator strip shown in the expanded card header. */
@Composable
fun IslandQueueStrip(
    count: Int,
    focusedIndex: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return
    Row(
        modifier = modifier.width(IslandDimensions.queueStripWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IslandQueueDots(count = count, focusedIndex = focusedIndex)
    }
}
