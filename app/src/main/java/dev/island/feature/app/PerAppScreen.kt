package dev.island.feature.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.island.R
import dev.island.domain.model.AppNotificationMode
import dev.island.domain.model.AppNotificationRule
import dev.island.domain.model.IslandSettings
import dev.island.feature.app.ui.EmptyState
import dev.island.feature.app.ui.EnumChoiceRow
import dev.island.feature.app.ui.IslandScaffold
import dev.island.feature.app.ui.RowDivider
import dev.island.feature.app.ui.SettingsCard
import dev.island.feature.app.ui.rememberSettingsUpdate

/**
 * Per-app notification rules.
 *
 * The list is built from apps that have actually posted a notification while Island was running
 * (`settings.notifications.seenApps`) — Island never enumerates installed apps, which is why it does
 * not need QUERY_ALL_PACKAGES and why the list starts empty.
 */
@Composable
fun PerAppScreen(settings: IslandSettings, onBack: () -> Unit) {
    val update = rememberSettingsUpdate()
    val seenApps = settings.notifications.seenApps.entries.sortedBy { it.value.lowercase() }

    val modeLabels = listOf(
        stringResource(R.string.per_app_mode_always),
        stringResource(R.string.per_app_mode_never),
        stringResource(R.string.per_app_mode_important),
        stringResource(R.string.per_app_mode_icon),
        stringResource(R.string.per_app_mode_full),
    )

    IslandScaffold(title = stringResource(R.string.per_app_title), onBack = onBack) { padding ->
        if (seenApps.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.per_app_empty),
                body = stringResource(R.string.per_app_empty_body),
                icon = Icons.Rounded.Apps,
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
            item {
                SettingsCard(contentPadding = 16.dp) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.per_app_body),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingsCard {
                    seenApps.forEachIndexed { index, (packageName, label) ->
                        if (index > 0) RowDivider()
                        val rule = settings.notifications.appRules[packageName]
                        EnumChoiceRow(
                            title = label,
                            summary = packageName,
                            values = AppNotificationMode.entries.toList(),
                            labels = modeLabels,
                            selected = rule?.mode ?: AppNotificationMode.ALWAYS,
                            onSelect = { mode ->
                                update { current ->
                                    val rules = current.notifications.appRules.toMutableMap()
                                    if (mode == AppNotificationMode.ALWAYS) {
                                        rules.remove(packageName)
                                    } else {
                                        rules[packageName] = AppNotificationRule(
                                            packageName = packageName,
                                            appLabel = label,
                                            mode = mode,
                                        )
                                    }
                                    current.copy(
                                        notifications = current.notifications.copy(appRules = rules),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
