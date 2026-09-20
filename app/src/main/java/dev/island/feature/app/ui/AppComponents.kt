package dev.island.feature.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.island.core.AppGraph
import dev.island.core.ui.theme.IslandColors
import dev.island.domain.model.IslandSettings
import kotlinx.coroutines.launch
import dev.island.core.ui.theme.IslandSpacing

/**
 * Building blocks for the settings/dashboard side of the app.
 *
 * Deliberately a handful of small, composable rows instead of a screen per pattern: every screen in
 * Island is a list of these, which keeps the app consistent, keeps each screen short, and means a
 * change to (say) the toggle row updates the whole product.
 */

enum class StatusTone { OK, WARN, ERROR, NEUTRAL }

fun StatusTone.color(): Color = when (this) {
    StatusTone.OK -> IslandColors.AccentGreen
    StatusTone.WARN -> IslandColors.AccentAmber
    StatusTone.ERROR -> IslandColors.AccentRed
    StatusTone.NEUTRAL -> IslandColors.TextTertiary
}

@Composable
fun IslandScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        content = content,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        modifier = modifier
            .padding(start = IslandSpacing.lg, end = IslandSpacing.lg, top = IslandSpacing.xl, bottom = IslandSpacing.sm)
            .semantics { heading() },
    )
}

/** Groups rows onto one surface, the way the system settings apps do. */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = IslandSpacing.md),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = IslandSpacing.lg),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = IslandSpacing.lg, vertical = IslandSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(IslandSpacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            RowTitle(title = title, enabled = enabled)
            if (summary != null) RowSummary(summary = summary, enabled = enabled)
        }
        Spacer(Modifier.width(IslandSpacing.sm))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun ClickRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = IslandSpacing.lg, vertical = IslandSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(IslandSpacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            RowTitle(title = title, enabled = enabled)
            if (summary != null) RowSummary(summary = summary, enabled = enabled)
        }
        if (trailing != null) {
            Spacer(Modifier.width(IslandSpacing.sm))
            trailing()
        }
    }
}

@Composable
private fun RowTitle(title: String, enabled: Boolean) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RowSummary(summary: String, enabled: Boolean) {
    Text(
        text = summary,
        style = MaterialTheme.typography.bodySmall,
        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
fun SliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    label: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = IslandSpacing.lg, vertical = IslandSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (label != null) {
                StatusChip(text = label, tone = StatusTone.NEUTRAL)
            }
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

/** Single-choice row rendered as a wrapped list of chips (enum settings). */
@Composable
fun ChoiceRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = IslandSpacing.lg, vertical = IslandSpacing.md),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (summary != null) RowSummary(summary = summary, enabled = true)
        Spacer(Modifier.height(IslandSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(IslandSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        )
                        .clickable { onSelect(index) }
                        .padding(horizontal = IslandSpacing.md, vertical = IslandSpacing.sm),
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun StatusChip(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tone.color().copy(alpha = 0.14f))
            .padding(horizontal = IslandSpacing.sm, vertical = IslandSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IslandSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tone.color()),
        )
        Text(
            text = text,
            color = tone.color(),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(IslandSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(IslandSpacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CheckMark(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    Icon(
        imageVector = Icons.Rounded.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(20.dp),
    )
}

/** Preview surface behind the in-app island preview: a fake status bar, not a screenshot. */
@Composable
fun PreviewBackdrop(
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(24.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFF2A3140), Color(0xFF12151B)),
                ),
            )
            .padding(IslandSpacing.md),
        contentAlignment = Alignment.TopCenter,
    ) {
        content()
    }
}

/**
 * Writes to the single source of truth (DataStore) from any screen. The overlay service observes the
 * same flow, so a change here is live on the island immediately — no restart, no re-binding.
 */
@Composable
fun rememberSettingsUpdate(): ((IslandSettings) -> IslandSettings) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope) { { transform -> scope.launch { AppGraph.settingsRepository.update(transform) } } }
}

/** Enum picker row: labels are resolved by the caller so the screen owns localisation. */
@Composable
fun <T> EnumChoiceRow(
    title: String,
    values: List<T>,
    labels: List<String>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    val index = values.indexOf(selected).coerceAtLeast(0)
    ChoiceRow(
        title = title,
        options = labels,
        selectedIndex = index,
        onSelect = { picked -> values.getOrNull(picked)?.let(onSelect) },
        modifier = modifier,
        summary = summary,
    )
}
