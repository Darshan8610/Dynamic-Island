package dev.island.feature.app

import android.os.Build
import android.os.Process
import android.os.SystemClock
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
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.island.BuildConfig
import dev.island.R
import dev.island.core.AppGraph
import dev.island.core.logging.asFlow
import dev.island.core.permissions.PermissionIntents
import dev.island.domain.model.IslandPhase
import dev.island.feature.app.ui.EmptyState
import dev.island.feature.app.ui.color
import dev.island.feature.app.ui.IslandScaffold
import dev.island.feature.app.ui.PermissionRow
import dev.island.feature.app.ui.RowDivider
import dev.island.feature.app.ui.SectionHeader
import dev.island.feature.app.ui.SettingsCard
import dev.island.feature.app.ui.StatusChip
import dev.island.feature.app.ui.StatusTone
import dev.island.feature.app.ui.permissionAppliesOnThisDevice
import java.text.DateFormat
import java.util.Date

/**
 * Diagnostics: what Island can do on *this* device, right now.
 *
 * Every row is read from the real runtime (permissions, listener connection, overlay attachment,
 * engine phase, cutout geometry, OEM battery restrictions) — nothing is a static checklist. This is
 * the screen that turns "it doesn't work" into "notification access is off", and it is also the
 * screen that explains why an OEM ROM kills the overlay.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uiState by AppGraph.engine.uiState.collectAsState()
    val permissions by AppGraph.permissionRepository.statuses.collectAsState(initial = emptyList())
    val serviceRunning by AppGraph.serviceRunning.collectAsState()
    val overlayAttached by AppGraph.overlayAttached.collectAsState()
    val listenerConnected by AppGraph.notificationAccess.collectAsState()
    val settings by AppGraph.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = dev.island.domain.model.IslandSettings.Default,
    )

    // Read the cutout from this window's real insets — never from a device table.
    val view = LocalView.current
    val cutout = remember(view) { AppGraph.cutoutDetector.detect(view) }
    val display = remember { AppGraph.displayInfoProvider.current() }
    val uptimeMs = remember { SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime() }
    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    LaunchedEffect(Unit) { AppGraph.permissionRepository.refresh() }

    IslandScaffold(title = stringResource(R.string.diagnostics_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item {
                SectionHeader(text = stringResource(R.string.diagnostics_section_state))
                SettingsCard {
                    DiagnosticRow(
                        label = stringResource(R.string.diagnostics_state_phase),
                        value = uiState.phase.name.lowercase(),
                        tone = when (uiState.phase) {
                            IslandPhase.DISABLED -> StatusTone.NEUTRAL
                            IslandPhase.IDLE -> StatusTone.OK
                            else -> StatusTone.OK
                        },
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.diagnostics_state_events),
                        value = uiState.activeEvents.size.toString(),
                        tone = StatusTone.NEUTRAL,
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.diagnostics_state_service),
                        value = stringResource(
                            if (serviceRunning) R.string.diagnostics_running else R.string.diagnostics_stopped,
                        ),
                        tone = if (serviceRunning) StatusTone.OK else StatusTone.WARN,
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.diagnostics_state_overlay),
                        value = stringResource(
                            if (overlayAttached) R.string.diagnostics_attached else R.string.diagnostics_detached,
                        ),
                        tone = if (overlayAttached) StatusTone.OK else StatusTone.ERROR,
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.diagnostics_state_listener),
                        value = stringResource(
                            if (listenerConnected) R.string.diagnostics_connected else R.string.diagnostics_disconnected,
                        ),
                        tone = if (listenerConnected) StatusTone.OK else StatusTone.WARN,
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.diagnostics_state_media),
                        value = stringResource(
                            if (AppGraph.mediaRepository.accessDenied) {
                                R.string.diagnostics_unavailable
                            } else {
                                R.string.diagnostics_available
                            },
                        ),
                        tone = if (AppGraph.mediaRepository.accessDenied) StatusTone.WARN else StatusTone.OK,
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.diagnostics_state_last_event),
                        value = if (uiState.lastEventAtMs > 0L) {
                            dateFormatter.format(Date(uiState.lastEventAtMs))
                        } else {
                            stringResource(R.string.diagnostics_none_yet)
                        },
                        tone = StatusTone.NEUTRAL,
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.diagnostics_state_uptime),
                        value = "${uptimeMs / 60_000L} min",
                        tone = StatusTone.NEUTRAL,
                    )
                }
            }

            item {
                SectionHeader(text = stringResource(R.string.diagnostics_section_capabilities))
                SettingsCard {
                    val visible = permissions.filter { permissionAppliesOnThisDevice(it.key) }
                    visible.forEachIndexed { index, status ->
                        if (index > 0) RowDivider()
                        PermissionRow(status = status)
                    }
                }
            }

            item {
                SectionHeader(text = stringResource(R.string.diagnostics_section_device))
                SettingsCard {
                    DiagnosticRow(
                        label = stringResource(R.string.diagnostics_device_model),
                        value = Build.MODEL,
                        tone = StatusTone.NEUTRAL,
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.diagnostics_device_manufacturer),
                        value = Build.MANUFACTURER,
                        tone = StatusTone.NEUTRAL,
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.diagnostics_device_android),
                        value = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        tone = StatusTone.NEUTRAL,
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.diagnostics_device_cutout),
                        value = stringResource(
                            if (cutout.hasCutout) R.string.diagnostics_cutout_found else R.string.diagnostics_cutout_none,
                        ) + " · ${display.widthDp.toInt()}×${display.heightDp.toInt()}dp",
                        tone = StatusTone.NEUTRAL,
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.diagnostics_status_bar),
                        value = "${cutout.statusBarHeightPx}px / safe top ${cutout.safeTopPx}px",
                        tone = StatusTone.NEUTRAL,
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.dashboard_row_battery),
                        value = stringResource(
                            if (AppGraph.permissionRepository.isIgnoringBatteryOptimizations()) {
                                R.string.diagnostics_running
                            } else {
                                R.string.permission_battery_title
                            },
                        ),
                        tone = if (AppGraph.permissionRepository.isIgnoringBatteryOptimizations()) {
                            StatusTone.OK
                        } else {
                            StatusTone.WARN
                        },
                    )
                }
            }

            item {
                SectionHeader(text = stringResource(R.string.diagnostics_section_oem))
                SettingsCard(contentPadding = 16.dp) {
                    Text(
                        text = stringResource(R.string.diagnostics_oem_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PermissionIntents.oemBatteryIntents(context).forEach { intent ->
                        TextButton(onClick = { AppGraph.permissionLauncher.launch(context, intent) }) {
                            Text(text = stringResource(R.string.diagnostics_open_oem_settings))
                        }
                    }
                    TextButton(
                        onClick = {
                            AppGraph.permissionLauncher.launch(
                                context,
                                PermissionIntents.batteryOptimizationSettings(context),
                            )
                        },
                    ) {
                        Text(text = stringResource(R.string.permission_battery_title))
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Island ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${settings.notifications.seenApps.size} apps seen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, tone: StatusTone) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (tone == StatusTone.NEUTRAL) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            StatusChip(text = value, tone = tone)
        }
    }
}

/**
 * In-memory event log (Settings → Advanced → Debug). Read-only, never persisted, redacted by the
 * logger before it ever reaches this list.
 */
@Composable
fun EventLogScreen(onBack: () -> Unit) {
    val entries by remember { AppGraph.logStore.asFlow() }.collectAsState(initial = AppGraph.logStore.snapshot())
    val dateFormatter = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }

    IslandScaffold(
        title = stringResource(R.string.event_log_title),
        onBack = onBack,
        actions = {
            TextButton(onClick = { AppGraph.logStore.clear() }) {
                Text(text = stringResource(R.string.event_log_clear))
            }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.event_log_empty),
                body = stringResource(R.string.event_log_body),
                icon = Icons.Rounded.BugReport,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@IslandScaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            text = dateFormatter.format(Date(entry.atMs)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "  ${entry.level.name}  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = when (entry.level) {
                                dev.island.core.logging.IslandLogLevel.ERROR -> MaterialTheme.colorScheme.error
                                dev.island.core.logging.IslandLogLevel.WARN -> StatusTone.WARN.color()
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = entry.tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(text = entry.message, style = MaterialTheme.typography.bodySmall)
                }
                RowDivider()
            }
        }
    }
}

/**
 * Performance screen. The FPS meter is a real [android.view.Choreographer] callback, so it reports
 * what the device is actually doing while the island animates — it is only started while this
 * screen is visible, which is the whole point of measuring on demand instead of all the time.
 */
@Composable
fun PerfScreen(onBack: () -> Unit) {
    val uiState by AppGraph.engine.uiState.collectAsState()
    val serviceRunning by AppGraph.serviceRunning.collectAsState()
    val overlayAttached by AppGraph.overlayAttached.collectAsState()

    var fps by remember { mutableFloatStateOf(0f) }
    var frameTimeMs by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val choreographer = android.view.Choreographer.getInstance()
        var frames = 0
        var windowStartNs = System.nanoTime()
        val callback = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frames++
                val elapsedNs = frameTimeNanos - windowStartNs
                if (elapsedNs >= 1_000_000_000L) {
                    fps = frames * 1_000_000_000f / elapsedNs
                    frameTimeMs = elapsedNs / 1_000_000f / frames.coerceAtLeast(1)
                    frames = 0
                    windowStartNs = frameTimeNanos
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose { choreographer.removeFrameCallback(callback) }
    }

    val runtime = remember { Runtime.getRuntime() }
    val usedMb = remember(fps) { (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) }

    IslandScaffold(title = stringResource(R.string.perf_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item {
                SectionHeader(text = stringResource(R.string.perf_fps))
                SettingsCard {
                    DiagnosticRow(
                        label = stringResource(R.string.perf_fps),
                        value = "%.1f fps".format(fps),
                        tone = if (fps > 50f) StatusTone.OK else StatusTone.WARN,
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.perf_frame_time),
                        value = "%.2f ms".format(frameTimeMs),
                        tone = if (frameTimeMs < 20f) StatusTone.OK else StatusTone.WARN,
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.perf_heap),
                        value = "$usedMb MB",
                        tone = StatusTone.NEUTRAL,
                    )
                }
            }
            item {
                SectionHeader(text = stringResource(R.string.perf_state))
                SettingsCard {
                    DiagnosticRow(
                        label = stringResource(R.string.perf_events),
                        value = uiState.activeEvents.size.toString(),
                        tone = StatusTone.NEUTRAL,
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.perf_overlay),
                        value = stringResource(
                            if (overlayAttached) R.string.diagnostics_attached else R.string.diagnostics_detached,
                        ),
                        tone = if (overlayAttached) StatusTone.OK else StatusTone.ERROR,
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.perf_service),
                        value = stringResource(
                            if (serviceRunning) R.string.diagnostics_running else R.string.diagnostics_stopped,
                        ),
                        tone = if (serviceRunning) StatusTone.OK else StatusTone.WARN,
                    )
                    RowDivider()
                    DiagnosticRow(
                        label = stringResource(R.string.perf_last_event),
                        value = if (uiState.lastEventAtMs > 0L) {
                            "${(System.currentTimeMillis() - uiState.lastEventAtMs) / 1000}s ago"
                        } else {
                            stringResource(R.string.diagnostics_none_yet)
                        },
                        tone = StatusTone.NEUTRAL,
                    )
                }
            }
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.perf_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
