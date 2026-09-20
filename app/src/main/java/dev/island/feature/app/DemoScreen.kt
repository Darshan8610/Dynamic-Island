package dev.island.feature.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.island.R
import dev.island.core.AppGraph
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandSettings
import dev.island.feature.app.ui.IslandScaffold
import dev.island.feature.app.ui.PreviewBackdrop
import dev.island.feature.app.ui.RowDivider
import dev.island.feature.app.ui.SectionHeader
import dev.island.feature.app.ui.SettingsCard
import dev.island.feature.app.ui.ToggleRow
import dev.island.feature.app.ui.rememberSettingsUpdate
import dev.island.feature.demo.DemoCategoryId
import dev.island.feature.demo.DemoEvents
import dev.island.feature.island.ui.IslandPreview

/**
 * Demo mode.
 *
 * Two distinct things, both useful:
 * - the **preview** renders a chosen sample through the production renderers, so every island type
 *   can be inspected without waiting for a real one;
 * - **send** pushes the sample through the real engine (priority, privacy masking, coalescing,
 *   expiration, state machine, overlay window), which is an honest end-to-end test.
 *
 * While demo mode is on, control actions are inert ([dev.island.service.IslandActionRunner]), so a
 * simulated call can never be answered and a simulated track can never pause real music.
 */
@Composable
fun DemoScreen(settings: IslandSettings, onBack: () -> Unit) {
    val update = rememberSettingsUpdate()
    var preview by remember { mutableStateOf<IslandEvent?>(null) }
    var previewExpanded by remember { mutableStateOf(false) }

    IslandScaffold(
        title = stringResource(R.string.demo_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = { AppGraph.engine.clear() }) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.demo_clear),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item {
                SettingsCard {
                    ToggleRow(
                        title = stringResource(R.string.settings_advanced_demo),
                        summary = stringResource(R.string.demo_body),
                        checked = settings.advanced.demoMode,
                        onCheckedChange = { on -> update { it.copy(advanced = it.advanced.copy(demoMode = on)) } },
                    )
                }
            }

            item {
                SectionHeader(text = stringResource(R.string.dashboard_preview_label))
                SettingsCard(contentPadding = 16.dp) {
                    val event = preview ?: remember { DemoEvents.music() }
                    PreviewBackdrop(height = if (previewExpanded) 250.dp else 150.dp) {
                        IslandPreview(
                            event = event,
                            settings = settings,
                            registry = AppGraph.rendererRegistry,
                            expanded = previewExpanded,
                            artworkProvider = AppGraph.artworkProvider,
                            appIconProvider = AppGraph.appIconProvider,
                        )
                    }
                    TextButton(onClick = { previewExpanded = !previewExpanded }) {
                        Text(
                            text = stringResource(
                                if (previewExpanded) R.string.a11y_island_collapse else R.string.a11y_island_expand,
                            ),
                        )
                    }
                }
            }

            demoSection(R.string.demo_section_notifications, NOTIFICATION_DEMOS) { category, event ->
                when (category) {
                    DemoCategoryId.NOTIFICATION, DemoCategoryId.GROUPED_NOTIFICATION -> {
                        preview = event
                    }

                    else -> Unit
                }
            }
            demoSection(R.string.demo_section_media, MEDIA_DEMOS) { _, event -> preview = event }
            demoSection(R.string.demo_section_timers, TIMER_DEMOS) { _, event -> preview = event }
            demoSection(R.string.demo_section_device, DEVICE_DEMOS) { _, event -> preview = event }
            demoSection(R.string.demo_section_other, OTHER_DEMOS) { _, event -> preview = event }
        }
    }
}

/** One section of demo rows: header + a single card of rows (list building, not composition). */
private fun LazyListScope.demoSection(
    @StringRes titleRes: Int,
    categories: List<Pair<DemoCategoryId, Int>>,
    onPreview: (DemoCategoryId, IslandEvent) -> Unit,
) {
    item { SectionHeader(text = stringResource(titleRes)) }
    item {
        SettingsCard {
            categories.forEachIndexed { index, (category, labelRes) ->
                if (index > 0) RowDivider()
                DemoRow(
                    label = stringResource(labelRes),
                    onPreview = {
                        DemoEvents.build(category)?.let { event -> onPreview(category, event) }
                    },
                    onSend = {
                        DemoEvents.build(category)?.let { event ->
                            onPreview(category, event)
                            AppGraph.engine.submit(event)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DemoRow(label: String, onPreview: () -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        TextButton(onClick = onPreview) {
            Text(text = stringResource(R.string.dashboard_preview_label))
        }
        TextButton(onClick = onSend) {
            Text(text = stringResource(R.string.action_test_overlay))
        }
    }
}

private val NOTIFICATION_DEMOS = listOf(
    DemoCategoryId.NOTIFICATION to R.string.demo_notification,
    DemoCategoryId.GROUPED_NOTIFICATION to R.string.demo_grouped_notification,
)

private val MEDIA_DEMOS = listOf(
    DemoCategoryId.MUSIC to R.string.demo_music,
    DemoCategoryId.MUSIC_PAUSED to R.string.island_paused,
)

private val TIMER_DEMOS = listOf(
    DemoCategoryId.TIMER to R.string.demo_timer,
    DemoCategoryId.TIMER_DONE to R.string.island_timer_complete,
    DemoCategoryId.STOPWATCH to R.string.demo_stopwatch,
)

private val DEVICE_DEMOS = listOf(
    DemoCategoryId.CALL_INCOMING to R.string.demo_call,
    DemoCategoryId.CALL_ACTIVE to R.string.demo_call_active,
    DemoCategoryId.CHARGING to R.string.demo_charging,
    DemoCategoryId.BATTERY_LOW to R.string.demo_battery_low,
    DemoCategoryId.BATTERY_FULL to R.string.island_battery_full,
    DemoCategoryId.HEADPHONES to R.string.island_headphones,
    DemoCategoryId.WATCH to R.string.island_watch,
    DemoCategoryId.ALARM to R.string.demo_alarm,
)

private val OTHER_DEMOS = listOf(
    DemoCategoryId.DOWNLOAD to R.string.demo_download,
    DemoCategoryId.NAVIGATION to R.string.demo_navigation,
    DemoCategoryId.CUSTOM to R.string.demo_custom,
)
