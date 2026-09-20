package dev.island.feature.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.island.R
import dev.island.core.AppGraph
import dev.island.domain.model.TimerInfo
import dev.island.domain.model.TimerPreset
import dev.island.feature.app.ui.EmptyState
import dev.island.feature.app.ui.IslandScaffold
import dev.island.feature.app.ui.RowDivider
import dev.island.feature.app.ui.SectionHeader
import dev.island.feature.app.ui.SettingsCard
import dev.island.feature.app.ui.StatusChip
import dev.island.feature.app.ui.StatusTone
import dev.island.feature.island.ui.widgets.IslandFormat
import kotlinx.coroutines.launch

/**
 * Timers & stopwatch.
 *
 * These are Island's own timers (not the system clock's), stored in [dev.island.data.timers.TimerEngine]:
 * they survive process death through AlarmManager, post a proper notification as a fallback when the
 * island is hidden, and drive the same island events as everything else. The UI is a thin layer over
 * the repository — the same code path the island's own buttons use.
 */
@Composable
fun TimersScreen(onBack: () -> Unit) {
    var stopwatchTab by remember { mutableStateOf(false) }
    val tabLabels = listOf(
        stringResource(R.string.timers_tab_countdown),
        stringResource(R.string.timers_tab_stopwatch),
    )

    IslandScaffold(title = stringResource(R.string.timers_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TabChip(
                    label = tabLabels[0],
                    selected = !stopwatchTab,
                    onClick = { stopwatchTab = false },
                )
                TabChip(
                    label = tabLabels[1],
                    selected = stopwatchTab,
                    onClick = { stopwatchTab = true },
                )
            }
            if (stopwatchTab) StopwatchSection() else CountdownSection()
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun CountdownSection() {
    val scope = rememberCoroutineScope()
    val timers by AppGraph.timerRepository.timers.collectAsState(initial = emptyList())
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("5") }
    var seconds by remember { mutableStateOf("0") }
    var label by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            SectionHeader(text = stringResource(R.string.timers_custom))
            SettingsCard(contentPadding = 16.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField(
                        value = hours,
                        onValueChange = { hours = it.filter(Char::isDigit).take(2) },
                        label = stringResource(R.string.timers_hours),
                    )
                    TimeField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(2) },
                        label = stringResource(R.string.timers_minutes),
                    )
                    TimeField(
                        value = seconds,
                        onValueChange = { seconds = it.filter(Char::isDigit).take(2) },
                        label = stringResource(R.string.timers_seconds),
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    label = { Text(text = stringResource(R.string.timers_label_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                val durationMs = ((hours.toIntOrNull() ?: 0) * 3_600_000L) +
                    ((minutes.toIntOrNull() ?: 0) * 60_000L) +
                    ((seconds.toIntOrNull() ?: 0) * 1_000L)
                Button(
                    onClick = {
                        scope.launch {
                            AppGraph.timerRepository.createTimer(
                                durationMs = durationMs,
                                label = label.trim().ifBlank { null },
                            )
                        }
                    },
                    enabled = durationMs > 0L,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.action_start))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.timers_custom_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimerPreset.entries.forEach { preset ->
                        Text(
                            text = stringResource(R.string.duration_minutes_short, preset.minutes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    scope.launch {
                                        AppGraph.timerRepository.createTimer(
                                            durationMs = preset.durationMs,
                                            label = null,
                                        )
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        item { SectionHeader(text = stringResource(R.string.timers_title)) }
        item {
            if (timers.isEmpty()) {
                SettingsCard {
                    EmptyState(
                        title = stringResource(R.string.timers_empty),
                        body = stringResource(R.string.timers_empty_body),
                        icon = Icons.Rounded.Timer,
                    )
                }
            } else {
                SettingsCard {
                    timers.forEachIndexed { index, timer ->
                        if (index > 0) RowDivider()
                        TimerRow(timer = timer)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.TimeField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun TimerRow(timer: TimerInfo) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timer.label ?: stringResource(R.string.timer_notification_default_label),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = IslandFormat.clock(timer.remainingMs),
                style = MaterialTheme.typography.titleMedium,
                color = if (timer.finished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        StatusChip(
            text = when {
                timer.finished -> stringResource(R.string.island_timer_complete)
                timer.running -> stringResource(R.string.diagnostics_running)
                else -> stringResource(R.string.island_paused)
            },
            tone = when {
                timer.finished -> StatusTone.ERROR
                timer.running -> StatusTone.OK
                else -> StatusTone.WARN
            },
        )
        IconButton(onClick = { scope.launch { AppGraph.timerRepository.toggleTimer(timer.timerId) } }) {
            Icon(
                imageVector = if (timer.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (timer.running) R.string.action_pause else R.string.action_resume),
            )
        }
        IconButton(onClick = { scope.launch { AppGraph.timerRepository.stopTimer(timer.timerId) } }) {
            Icon(
                imageVector = Icons.Rounded.Stop,
                contentDescription = stringResource(R.string.action_stop),
            )
        }
    }
}

@Composable
private fun StopwatchSection() {
    val scope = rememberCoroutineScope()
    val stopwatch by AppGraph.timerRepository.stopwatch.collectAsState(initial = null)
    val state = stopwatch
    val running = state?.running == true

    // The stopwatch is the one screen that needs its own tick; 10Hz matches the tenths we render.
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(STOPWATCH_TICK_MS)
            now = android.os.SystemClock.elapsedRealtime()
        }
    }
    val elapsed = state?.elapsedAt(now) ?: 0L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            SectionHeader(text = stringResource(R.string.timers_tab_stopwatch))
            SettingsCard(contentPadding = 16.dp) {
                Text(
                    text = IslandFormat.clockWithTenths(elapsed),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { scope.launch { AppGraph.timerRepository.toggleStopwatch() } },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = if (state?.running == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(Modifier.height(0.dp))
                        Text(
                            text = stringResource(
                                if (state?.running == true) R.string.action_pause else R.string.action_start,
                            ),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    IconButton(onClick = { scope.launch { AppGraph.timerRepository.lapStopwatch() } }) {
                        Icon(imageVector = Icons.Rounded.Flag, contentDescription = stringResource(R.string.action_lap))
                    }
                    IconButton(onClick = { scope.launch { AppGraph.timerRepository.resetStopwatch() } }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.action_reset),
                        )
                    }
                }
            }
        }

        item { SectionHeader(text = stringResource(R.string.stopwatch_laps)) }
        item {
            val laps = state?.laps.orEmpty()
            if (laps.isEmpty()) {
                SettingsCard {
                    EmptyState(
                        title = stringResource(R.string.stopwatch_empty),
                        body = stringResource(R.string.stopwatch_split),
                        icon = Icons.Rounded.Flag,
                    )
                }
            } else {
                SettingsCard {
                    val splits = state?.splits().orEmpty()
                    laps.forEachIndexed { index, lap ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.stopwatch_lap_number, index + 1),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = IslandFormat.clockWithTenths(splits.getOrElse(index) { lap }),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (index != laps.lastIndex) RowDivider()
                    }
                }
            }
        }
    }
}

private const val STOPWATCH_TICK_MS = 100L
