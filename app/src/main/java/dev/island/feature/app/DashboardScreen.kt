package dev.island.feature.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.island.R
import dev.island.core.AppGraph
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.PermissionKey
import dev.island.feature.app.ui.IslandScaffold
import dev.island.feature.app.ui.PreviewBackdrop
import dev.island.feature.app.ui.RowDivider
import dev.island.feature.app.ui.SectionHeader
import dev.island.feature.app.ui.SettingsCard
import dev.island.feature.app.ui.ClickRow
import dev.island.feature.app.ui.StatusChip
import dev.island.feature.app.ui.StatusTone
import dev.island.feature.app.ui.ToggleRow
import dev.island.feature.app.ui.PermissionRow
import dev.island.feature.app.ui.permissionAppliesOnThisDevice
import dev.island.feature.demo.DemoEvents
import dev.island.feature.island.ui.IslandPreview
import dev.island.service.IslandService
import kotlinx.coroutines.launch

/**
 * Dashboard: the honest answer to "is it working?".
 *
 * Shows the live state of every capability (overlay permission, notification listener, service,
 * engine phase, active events), a real preview of the island rendered by the production renderers,
 * and one-tap fixes for anything missing. Nothing here is simulated except the preview event.
 */
@Composable
fun DashboardScreen(
    settings: IslandSettings,
    onNavigate: (String) -> Unit,
    onBack: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by AppGraph.engine.uiState.collectAsState()
    val permissions by AppGraph.permissionRepository.statuses.collectAsState(initial = emptyList())
    val serviceRunning by AppGraph.serviceRunning.collectAsState()
    val overlayAttached by AppGraph.overlayAttached.collectAsState()
    val listenerConnected by AppGraph.notificationAccess.collectAsState()

    // Permission state lives in system settings, which return without a callback: re-read on resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) AppGraph.permissionRepository.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val previewEvent = remember { DemoEvents.music() }

    IslandScaffold(
        title = stringResource(R.string.dashboard_title),
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                SectionHeader(text = stringResource(R.string.dashboard_section_status))
                SettingsCard {
                    ToggleRow(
                        title = stringResource(R.string.settings_island_enable),
                        summary = stringResource(R.string.settings_island_enable_summary),
                        checked = settings.islandEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                AppGraph.settingsRepository.update { it.copy(islandEnabled = enabled) }
                            }
                            if (enabled) IslandService.start(context)
                        },
                    )
                    RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusChip(
                            text = stringResource(
                                when {
                                    !settings.islandEnabled -> R.string.dashboard_status_off
                                    serviceRunning -> R.string.dashboard_status_active
                                    else -> R.string.dashboard_status_paused
                                },
                            ),
                            tone = when {
                                !settings.islandEnabled -> StatusTone.NEUTRAL
                                serviceRunning -> StatusTone.OK
                                else -> StatusTone.WARN
                            },
                        )
                        StatusChip(
                            text = stringResource(
                                if (overlayAttached) R.string.diagnostics_attached else R.string.diagnostics_detached,
                            ),
                            tone = if (overlayAttached) StatusTone.OK else StatusTone.WARN,
                        )
                        StatusChip(
                            text = uiState.activeEvents.size.toString(),
                            tone = StatusTone.NEUTRAL,
                        )
                    }
                }
            }

            item {
                SectionHeader(text = stringResource(R.string.dashboard_preview_label))
                SettingsCard(contentPadding = 16.dp) {
                    PreviewBackdrop {
                        IslandPreview(
                            event = previewEvent,
                            settings = settings,
                            registry = AppGraph.rendererRegistry,
                            artworkProvider = AppGraph.artworkProvider,
                            appIconProvider = AppGraph.appIconProvider,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.dashboard_empty_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item { SectionHeader(text = stringResource(R.string.dashboard_section_permissions)) }
            item {
                SettingsCard {
                    val visible = permissions.filter { permissionAppliesOnThisDevice(it.key) }
                    if (visible.isEmpty()) {
                        ClickRow(
                            title = stringResource(R.string.dashboard_state_unavailable),
                            summary = stringResource(R.string.permission_recovery_title),
                            onClick = { AppGraph.permissionRepository.refresh() },
                        )
                    }
                    visible.forEachIndexed { index, status ->
                        if (index > 0) RowDivider()
                        PermissionRow(status = status)
                    }
                }
            }

            item { SectionHeader(text = stringResource(R.string.dashboard_section_actions)) }
            item {
                SettingsCard {
                    ClickRow(
                        title = stringResource(R.string.settings_title),
                        summary = stringResource(R.string.settings_section_island),
                        onClick = { onNavigate(Routes.SETTINGS) },
                    )
                    RowDivider()
                    ClickRow(
                        title = stringResource(R.string.customize_title),
                        summary = stringResource(R.string.customize_body),
                        onClick = { onNavigate(Routes.CUSTOMIZE) },
                    )
                    RowDivider()
                    ClickRow(
                        title = stringResource(R.string.timers_title),
                        summary = stringResource(R.string.timers_empty_body),
                        onClick = { onNavigate(Routes.TIMERS) },
                    )
                    RowDivider()
                    ClickRow(
                        title = stringResource(R.string.demo_title),
                        summary = stringResource(R.string.demo_body),
                        leading = {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = { onNavigate(Routes.DEMO) },
                    )
                    RowDivider()
                    ClickRow(
                        title = stringResource(R.string.diagnostics_title),
                        summary = stringResource(R.string.diagnostics_body),
                        onClick = { onNavigate(Routes.DIAGNOSTICS) },
                    )
                    RowDivider()
                    ClickRow(
                        title = stringResource(R.string.history_title),
                        summary = if (settings.privacy.historyEnabled) {
                            stringResource(R.string.history_body)
                        } else {
                            stringResource(R.string.history_disabled)
                        },
                        onClick = { onNavigate(Routes.HISTORY) },
                    )
                }
            }

            if (!listenerConnected && settings.notifications.enabled) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.permission_listener_how),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
