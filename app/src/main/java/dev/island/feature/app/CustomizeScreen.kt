package dev.island.feature.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.island.R
import dev.island.core.AppGraph
import dev.island.domain.model.IslandAppearance
import dev.island.domain.model.IslandPreset
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.IslandThemeMode
import dev.island.feature.app.ui.CheckMark
import dev.island.feature.app.ui.EnumChoiceRow
import dev.island.feature.app.ui.IslandScaffold
import dev.island.feature.app.ui.PreviewBackdrop
import dev.island.feature.app.ui.RowDivider
import dev.island.feature.app.ui.SectionHeader
import dev.island.feature.app.ui.SettingsCard
import dev.island.feature.app.ui.SliderRow
import dev.island.feature.app.ui.ToggleRow
import dev.island.feature.app.ui.rememberSettingsUpdate
import dev.island.feature.demo.DemoEvents
import dev.island.feature.island.ui.IslandPreview

/** Accent swatches offered in Customize. Raw ARGB, so nothing depends on Compose color parsing. */
private val accentSwatches = listOf(
    null to "Default",
    0xFF7CC4FF to "Sky",
    0xFF6BD968 to "Green",
    0xFFFFC46B to "Amber",
    0xFFFF6B6B to "Coral",
    0xFFB79CFF to "Violet",
    0xFFFFFFFF to "White",
)

/**
 * Customize: everything about how the island looks, with a live preview rendered by the production
 * renderers — not an approximation. Sliders write straight to DataStore, and the overlay observes
 * the same flow, so the real island changes while the user drags.
 */
@Composable
fun CustomizeScreen(settings: IslandSettings, onBack: () -> Unit) {
    val update = rememberSettingsUpdate()
    val appearance = settings.appearance
    var expandedPreview by remember { mutableStateOf(false) }
    val previewEvent = remember(expandedPreview) {
        if (expandedPreview) DemoEvents.music() else DemoEvents.charging()
    }

    val presetLabels = listOf(
        stringResource(R.string.preset_minimal),
        stringResource(R.string.preset_classic),
        stringResource(R.string.preset_compact),
        stringResource(R.string.preset_large),
        stringResource(R.string.preset_gaming),
        stringResource(R.string.preset_music),
        stringResource(R.string.preset_transparent),
        stringResource(R.string.preset_dynamic),
        stringResource(R.string.preset_custom),
    )
    val themeLabels = listOf(
        stringResource(R.string.theme_pure_black),
        stringResource(R.string.theme_dark_gray),
        stringResource(R.string.theme_dynamic),
    )

    IslandScaffold(title = stringResource(R.string.customize_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item {
                SectionHeader(text = stringResource(R.string.dashboard_preview_label))
                SettingsCard(contentPadding = 16.dp) {
                    PreviewBackdrop(height = if (expandedPreview) 240.dp else 150.dp) {
                        IslandPreview(
                            event = previewEvent,
                            settings = settings,
                            registry = AppGraph.rendererRegistry,
                            expanded = expandedPreview,
                            artworkProvider = AppGraph.artworkProvider,
                            appIconProvider = AppGraph.appIconProvider,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreviewTab(
                            label = stringResource(R.string.a11y_island_collapsed),
                            selected = !expandedPreview,
                            onClick = { expandedPreview = false },
                        )
                        PreviewTab(
                            label = stringResource(R.string.a11y_island_expanded),
                            selected = expandedPreview,
                            onClick = { expandedPreview = true },
                        )
                    }
                }
            }

            item {
                SectionHeader(text = stringResource(R.string.settings_appearance_presets))
                SettingsCard {
                    EnumChoiceRow(
                        title = stringResource(R.string.customize_section_shape),
                        values = IslandPreset.entries.toList(),
                        labels = presetLabels,
                        selected = appearance.preset,
                        onSelect = { preset ->
                            update { current ->
                                current.copy(appearance = preset.apply(current.appearance))
                            }
                        },
                    )
                    RowDivider()
                    ClickableResetRow(
                        title = stringResource(R.string.customize_reset),
                        onClick = { update { it.copy(appearance = IslandAppearance()) } },
                    )
                }
            }

            item {
                SectionHeader(text = stringResource(R.string.customize_section_shape))
                SettingsCard {
                    SliderRow(
                        title = stringResource(R.string.settings_island_size),
                        value = appearance.sizeScale,
                        onValueChange = { value -> update { it.copy(appearance = it.appearance.copy(sizeScale = value)) } },
                        valueRange = IslandAppearance.MIN_SIZE_SCALE..IslandAppearance.MAX_SIZE_SCALE,
                        label = stringResource(R.string.value_percent, (appearance.sizeScale * 100).toInt()),
                    )
                    RowDivider()
                    SliderRow(
                        title = stringResource(R.string.settings_island_corner_radius),
                        value = appearance.cornerRadiusDp,
                        onValueChange = { value ->
                            update { it.copy(appearance = it.appearance.copy(cornerRadiusDp = value)) }
                        },
                        valueRange = 8f..40f,
                        label = "${appearance.cornerRadiusDp.toInt()}dp",
                    )
                    RowDivider()
                    SliderRow(
                        title = stringResource(R.string.settings_island_horizontal_offset),
                        value = appearance.horizontalOffsetDp,
                        onValueChange = { value ->
                            update { it.copy(appearance = it.appearance.copy(horizontalOffsetDp = value)) }
                        },
                        valueRange = -40f..40f,
                        label = "${appearance.horizontalOffsetDp.toInt()}dp",
                    )
                    RowDivider()
                    SliderRow(
                        title = stringResource(R.string.settings_island_vertical_offset),
                        value = appearance.verticalOffsetDp,
                        onValueChange = { value ->
                            update { it.copy(appearance = it.appearance.copy(verticalOffsetDp = value)) }
                        },
                        valueRange = -12f..40f,
                        label = "${appearance.verticalOffsetDp.toInt()}dp",
                    )
                    RowDivider()
                    SliderRow(
                        title = stringResource(R.string.settings_island_icon_size),
                        value = appearance.iconScale,
                        onValueChange = { value -> update { it.copy(appearance = it.appearance.copy(iconScale = value)) } },
                        valueRange = 0.7f..1.5f,
                        label = stringResource(R.string.value_percent, (appearance.iconScale * 100).toInt()),
                    )
                    RowDivider()
                    SliderRow(
                        title = stringResource(R.string.settings_island_text_size),
                        value = appearance.textScale,
                        onValueChange = { value -> update { it.copy(appearance = it.appearance.copy(textScale = value)) } },
                        valueRange = 0.8f..1.4f,
                        label = stringResource(R.string.value_percent, (appearance.textScale * 100).toInt()),
                    )
                }
            }

            item {
                SectionHeader(text = stringResource(R.string.customize_section_color))
                SettingsCard {
                    EnumChoiceRow(
                        title = stringResource(R.string.settings_appearance_theme),
                        values = IslandThemeMode.entries.toList(),
                        labels = themeLabels,
                        selected = appearance.themeMode,
                        onSelect = { mode -> update { it.copy(appearance = it.appearance.copy(themeMode = mode)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_appearance_dynamic_color),
                        summary = stringResource(R.string.settings_appearance_dynamic_color_summary),
                        checked = appearance.dynamicColor,
                        onCheckedChange = { on -> update { it.copy(appearance = it.appearance.copy(dynamicColor = on)) } },
                    )
                    RowDivider()
                    SliderRow(
                        title = stringResource(R.string.settings_appearance_transparency),
                        value = appearance.opacity,
                        onValueChange = { value -> update { it.copy(appearance = it.appearance.copy(opacity = value)) } },
                        valueRange = 0.4f..1f,
                        label = stringResource(R.string.value_percent, (appearance.opacity * 100).toInt()),
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_appearance_blur),
                        summary = stringResource(R.string.settings_appearance_blur_summary),
                        checked = appearance.blurEnabled,
                        onCheckedChange = { on -> update { it.copy(appearance = it.appearance.copy(blurEnabled = on)) } },
                    )
                    RowDivider()
                    SliderRow(
                        title = stringResource(R.string.settings_appearance_blur),
                        value = appearance.blurRadius,
                        onValueChange = { value -> update { it.copy(appearance = it.appearance.copy(blurRadius = value)) } },
                        valueRange = 4f..40f,
                        label = "${appearance.blurRadius.toInt()}",
                    )
                    RowDivider()
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_appearance_accent),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            accentSwatches.forEach { (argb, _) ->
                                val selected = appearance.accentArgb == argb
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(argb?.let { Color(it) } ?: Color(0xFF2A2D33))
                                        .clickable {
                                            update { it.copy(appearance = it.appearance.copy(accentArgb = argb)) }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CheckMark(visible = selected)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.customize_accent_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SectionHeader(text = stringResource(R.string.customize_section_motion))
                SettingsCard {
                    SliderRow(
                        title = stringResource(R.string.settings_island_animation_intensity),
                        value = appearance.animationSpeed,
                        onValueChange = { value ->
                            update { it.copy(appearance = it.appearance.copy(animationSpeed = value)) }
                        },
                        valueRange = IslandAppearance.MIN_ANIMATION_SPEED..IslandAppearance.MAX_ANIMATION_SPEED,
                        label = "${"%.2f".format(appearance.animationSpeed)}×",
                    )
                    RowDivider()
                    ClickableResetRow(
                        title = stringResource(R.string.customize_preview_event),
                        onClick = { AppGraph.engine.submit(DemoEvents.custom()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClickableResetRow(title: String, onClick: () -> Unit) {
    dev.island.feature.app.ui.ClickRow(title = title, onClick = onClick, modifier = Modifier.fillMaxWidth())
}
