package dev.island.feature.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.island.R
import dev.island.core.AppGraph
import dev.island.core.permissions.PermissionIntents
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.PermissionKey
import dev.island.feature.app.ui.IslandScaffold
import dev.island.feature.app.ui.PreviewBackdrop
import dev.island.feature.app.ui.rememberSettingsUpdate
import dev.island.feature.demo.DemoEvents
import dev.island.feature.island.ui.IslandPreview
import dev.island.service.IslandService
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGES = 5

/**
 * Onboarding: five pages, each one asking for exactly what it explains.
 *
 * Order matters — overlay first (without it nothing is visible), then notification access (the
 * biggest capability, and the one Android hides deepest), then a live test so the user sees the
 * island working before they commit. Every page can be skipped: Island is useful with the overlay
 * alone (timers, charging, media), so no step is a hard gate.
 */
@Composable
fun OnboardingScreen(settings: IslandSettings, onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val update = rememberSettingsUpdate()
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGES })
    val permissions by AppGraph.permissionRepository.statuses.collectAsState(initial = emptyList())
    val previewEvent = remember { DemoEvents.music() }

    val titles = listOf(
        stringResource(R.string.onboarding_welcome_title),
        stringResource(R.string.onboarding_overlay_title),
        stringResource(R.string.onboarding_notifications_title),
        stringResource(R.string.onboarding_media_title),
        stringResource(R.string.onboarding_done_title),
    )
    val bodies = listOf(
        stringResource(R.string.onboarding_welcome_body),
        stringResource(R.string.onboarding_overlay_body),
        stringResource(R.string.onboarding_notifications_body),
        stringResource(R.string.onboarding_media_body),
        stringResource(R.string.onboarding_done_body),
    )

    fun permissionGranted(key: PermissionKey): Boolean =
        permissions.firstOrNull { it.key == key }?.granted == true

    IslandScaffold(title = stringResource(R.string.app_name)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = stringResource(R.string.onboarding_step_of, pagerState.currentPage + 1, ONBOARDING_PAGES),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = titles[page],
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = bodies[page],
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))

                    when (page) {
                        0 -> OnboardingArt(icon = Icons.Rounded.Smartphone)
                        1 -> OnboardingPermissionBlock(
                            granted = permissionGranted(PermissionKey.OVERLAY),
                            grantedLabel = stringResource(R.string.dashboard_state_granted),
                            actionLabel = stringResource(R.string.permission_overlay_title),
                            onAction = {
                                AppGraph.permissionLauncher.launch(
                                    context,
                                    PermissionIntents.overlaySettings(context),
                                )
                                AppGraph.permissionRepository.refresh()
                            },
                        )

                        2 -> OnboardingPermissionBlock(
                            granted = permissionGranted(PermissionKey.NOTIFICATION_LISTENER),
                            grantedLabel = stringResource(R.string.dashboard_state_granted),
                            actionLabel = stringResource(R.string.permission_listener_title),
                            hint = stringResource(R.string.permission_listener_how),
                            onAction = {
                                AppGraph.permissionLauncher.launch(
                                    context,
                                    PermissionIntents.notificationListenerSettings(context),
                                )
                                AppGraph.permissionRepository.refresh()
                            },
                        )

                        3 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PreviewBackdrop(height = 170.dp) {
                                IslandPreview(
                                    event = previewEvent,
                                    settings = settings,
                                    registry = AppGraph.rendererRegistry,
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            PreviewBackdrop(height = 230.dp) {
                                IslandPreview(
                                    event = previewEvent,
                                    settings = settings,
                                    registry = AppGraph.rendererRegistry,
                                    expanded = true,
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = {
                                    update { it.copy(islandEnabled = true) }
                                    IslandService.start(context)
                                    AppGraph.engine.submit(DemoEvents.music())
                                },
                            ) {
                                Text(text = stringResource(R.string.action_test_overlay))
                            }
                        }

                        else -> OnboardingArt(icon = Icons.Rounded.CheckCircle)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(ONBOARDING_PAGES) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        update { it.copy(onboardingComplete = true) }
                        onFinished()
                    },
                ) {
                    Text(text = stringResource(R.string.action_skip))
                }
                Spacer(Modifier.weight(1f))
                if (pagerState.currentPage > 0) {
                    OutlinedButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                        Text(text = stringResource(R.string.action_back))
                    }
                }
                Button(
                    onClick = {
                        if (pagerState.currentPage == ONBOARDING_PAGES - 1) {
                            update { it.copy(onboardingComplete = true, islandEnabled = true) }
                            IslandService.start(context)
                            onFinished()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                ) {
                    Text(
                        text = stringResource(
                            if (pagerState.currentPage == ONBOARDING_PAGES - 1) {
                                R.string.action_finish
                            } else {
                                R.string.action_continue
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingArt(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(72.dp),
    )
}

@Composable
private fun OnboardingPermissionBlock(
    granted: Boolean,
    grantedLabel: String,
    actionLabel: String,
    onAction: () -> Unit,
    hint: String? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = onAction, enabled = !granted) {
            Text(text = if (granted) grantedLabel else actionLabel)
        }
        if (hint != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
