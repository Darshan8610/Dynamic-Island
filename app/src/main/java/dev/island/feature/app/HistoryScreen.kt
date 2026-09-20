package dev.island.feature.app

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.island.R
import dev.island.core.AppGraph
import dev.island.domain.model.HistoryAction
import dev.island.domain.model.HistoryEntry
import dev.island.domain.model.IslandEventType
import dev.island.domain.model.IslandSettings
import dev.island.feature.app.ui.EmptyState
import dev.island.feature.app.ui.IslandScaffold
import dev.island.feature.app.ui.RowDivider
import dev.island.feature.app.ui.SettingsCard
import dev.island.feature.app.ui.ToggleRow
import dev.island.feature.app.ui.rememberSettingsUpdate
import dev.island.feature.island.ui.widgets.iconFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Event history — off by default, and metadata-only unless the user explicitly opts into content.
 *
 * The screen is honest about that: when history is disabled it says so and offers the switch; when
 * content storage is off, rows show type, source and timing but never notification text. Export
 * writes to the app's own cache directory and hands a content URI to the system share sheet — the
 * file is never uploaded anywhere and never leaves the device on its own.
 */
@Composable
fun HistoryScreen(settings: IslandSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val update = rememberSettingsUpdate()
    val entries by AppGraph.historyRepository.entries.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }

    LaunchedEffect(settings.privacy.historyEnabled) {
        if (settings.privacy.historyEnabled) AppGraph.historyRepository.ensureLoaded()
    }

    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    IslandScaffold(
        title = stringResource(R.string.history_title),
        onBack = onBack,
        actions = {
            if (settings.privacy.historyEnabled && entries.isNotEmpty()) {
                IconButton(
                    onClick = {
                        scope.launch {
                            val text = AppGraph.historyRepository.exportAsText()
                            val shared = exportToFile(context, text)
                            if (shared != null) context.startActivity(shared)
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.history_export),
                    )
                }
                IconButton(onClick = { scope.launch { AppGraph.historyRepository.clear() } }) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = stringResource(R.string.history_clear),
                    )
                }
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
                        title = stringResource(R.string.settings_privacy_history),
                        summary = stringResource(R.string.settings_privacy_history_summary),
                        checked = settings.privacy.historyEnabled,
                        onCheckedChange = { on -> update { it.copy(privacy = it.privacy.copy(historyEnabled = on)) } },
                    )
                    RowDivider()
                    ToggleRow(
                        title = stringResource(R.string.settings_privacy_history_content),
                        summary = stringResource(R.string.settings_privacy_history_content_summary),
                        checked = settings.privacy.historyStoresContent,
                        onCheckedChange = { on ->
                            update { it.copy(privacy = it.privacy.copy(historyStoresContent = on)) }
                        },
                        enabled = settings.privacy.historyEnabled,
                    )
                }
            }

            if (!settings.privacy.historyEnabled) {
                item {
                    EmptyState(
                        title = stringResource(R.string.history_disabled),
                        body = stringResource(R.string.history_body),
                        icon = Icons.Rounded.History,
                    )
                }
                return@LazyColumn
            }

            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(text = stringResource(R.string.history_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.history_entries, entries.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            val filtered = entries.filter { entry ->
                query.isBlank() ||
                    (entry.titlePreview ?: "").contains(query, ignoreCase = true) ||
                    (entry.sourceLabel ?: "").contains(query, ignoreCase = true) ||
                    entry.type.name.contains(query, ignoreCase = true)
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.history_empty),
                        body = stringResource(R.string.history_empty_body),
                        icon = Icons.Rounded.History,
                    )
                }
            } else {
                item {
                    SettingsCard {
                        filtered.forEachIndexed { index, entry ->
                            if (index > 0) RowDivider()
                            HistoryRow(
                                entry = entry,
                                showContent = settings.privacy.historyStoresContent,
                                dateFormatter = dateFormatter,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, showContent: Boolean, dateFormatter: DateFormat) {
    val actionLabel = stringResource(
        when (entry.action) {
            HistoryAction.SHOWN -> R.string.history_action_shown
            HistoryAction.EXPANDED -> R.string.history_action_expanded
            HistoryAction.COLLAPSED -> R.string.history_action_collapsed
            HistoryAction.DISMISSED -> R.string.history_action_dismissed
            HistoryAction.EXPIRED -> R.string.history_action_expired
            HistoryAction.ACTION_TAPPED -> R.string.history_action_tapped
            HistoryAction.SUPPRESSED -> R.string.history_action_suppressed
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = iconFor(iconKeyForType(entry.type)),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    showContent && !entry.titlePreview.isNullOrBlank() -> entry.titlePreview
                    else -> entry.sourceLabel ?: typeLabel(entry.type)
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text = "${dateFormatter.format(Date(entry.atMs))} · $actionLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        entry.shownForMs?.let {
            Text(
                text = "${it / 1000}s",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun typeLabel(type: IslandEventType): String = stringResource(
    when (type) {
        IslandEventType.NOTIFICATION -> R.string.island_new_notification
        IslandEventType.MEDIA -> R.string.island_now_playing
        IslandEventType.TIMER -> R.string.island_timer
        IslandEventType.STOPWATCH -> R.string.island_stopwatch
        IslandEventType.CALL -> R.string.island_ongoing_call
        IslandEventType.CHARGING -> R.string.island_charging
        IslandEventType.BATTERY -> R.string.settings_section_battery
        IslandEventType.BLUETOOTH -> R.string.island_bluetooth_device
        IslandEventType.NAVIGATION -> R.string.island_navigation
        IslandEventType.DOWNLOAD -> R.string.island_downloading
        IslandEventType.ALARM -> R.string.island_alarm
        IslandEventType.SYSTEM -> R.string.diagnostics_section_state
        IslandEventType.CUSTOM -> R.string.customize_title
    },
)

private fun iconKeyForType(type: IslandEventType) = when (type) {
    IslandEventType.NOTIFICATION -> dev.island.domain.model.IslandIconKey.NOTIFICATION
    IslandEventType.MEDIA -> dev.island.domain.model.IslandIconKey.MUSIC
    IslandEventType.TIMER -> dev.island.domain.model.IslandIconKey.TIMER
    IslandEventType.STOPWATCH -> dev.island.domain.model.IslandIconKey.STOPWATCH
    IslandEventType.CALL -> dev.island.domain.model.IslandIconKey.CALL_ACTIVE
    IslandEventType.CHARGING -> dev.island.domain.model.IslandIconKey.BATTERY_CHARGING
    IslandEventType.BATTERY -> dev.island.domain.model.IslandIconKey.BATTERY_LOW
    IslandEventType.BLUETOOTH -> dev.island.domain.model.IslandIconKey.BLUETOOTH
    IslandEventType.NAVIGATION -> dev.island.domain.model.IslandIconKey.NAVIGATION
    IslandEventType.DOWNLOAD -> dev.island.domain.model.IslandIconKey.DOWNLOAD
    IslandEventType.ALARM -> dev.island.domain.model.IslandIconKey.ALARM
    IslandEventType.SYSTEM -> dev.island.domain.model.IslandIconKey.INFO
    IslandEventType.CUSTOM -> dev.island.domain.model.IslandIconKey.CUSTOM
}

/** Writes the export into the FileProvider-shared cache path and returns a share intent. */
private suspend fun exportToFile(context: android.content.Context, text: String): Intent? =
    withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(directory, "island-history-${System.currentTimeMillis()}.txt")
            file.writeText(text)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.event_log_share_title))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }.getOrNull()
    }
