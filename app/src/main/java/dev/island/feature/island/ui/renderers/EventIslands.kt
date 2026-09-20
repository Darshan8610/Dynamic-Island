package dev.island.feature.island.ui.renderers

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.island.R
import dev.island.core.ui.theme.IslandColors
import dev.island.core.ui.theme.IslandDimensions
import dev.island.core.ui.theme.IslandMotion
import dev.island.core.ui.theme.IslandSpacing
import dev.island.core.ui.theme.IslandTypography
import dev.island.core.ui.theme.resolveAccent
import dev.island.domain.model.CallState
import dev.island.domain.model.ConnectedDeviceKind
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandIconKey
import dev.island.domain.model.ManeuverKind
import dev.island.domain.model.PlugType
import dev.island.feature.island.ui.widgets.IslandActionRow
import dev.island.feature.island.ui.widgets.IslandAppIcon
import dev.island.feature.island.ui.widgets.IslandChip
import dev.island.feature.island.ui.widgets.IslandCollapsedRow
import dev.island.feature.island.ui.widgets.IslandDivider
import dev.island.feature.island.ui.widgets.IslandExpandedCard
import dev.island.feature.island.ui.widgets.IslandFormat
import dev.island.feature.island.ui.widgets.IslandHeader
import dev.island.feature.island.ui.widgets.IslandIcon
import dev.island.feature.island.ui.widgets.IslandMetric
import dev.island.feature.island.ui.widgets.IslandProgress
import dev.island.feature.island.ui.widgets.IslandQueueStrip
import dev.island.feature.island.ui.widgets.IslandSectionLabel
import dev.island.feature.island.ui.widgets.IslandText

/**
 * One renderer per event family.
 *
 * Every renderer is built from the same three slots — collapsed, expanded, minimized — through
 * [SimpleIslandRenderer], so adding a new event type means adding one entry to
 * [defaultIslandRenderers] and never touching the shell, the window manager or the engine.
 *
 * Renderers read live values from the payload (timer remaining, call duration, media position)
 * using the shared ticker in [IslandRenderContext.nowElapsedMs]; they never poll and never own a
 * coroutine. Everything the user can tap goes back out through [IslandRenderContext.onAction], so
 * no renderer holds an Android dependency.
 */
class SimpleIslandRenderer(
    override val order: Int,
    private val matches: (IslandEvent) -> Boolean,
    private val collapsed: @Composable (IslandEvent, Modifier) -> Unit,
    private val expanded: @Composable (IslandEvent, Modifier) -> Unit,
    private val minimized: @Composable (IslandEvent, Modifier) -> Unit,
) : IslandRenderer {

    override fun canRender(event: IslandEvent): Boolean = matches(event)

    @Composable
    override fun Render(event: IslandEvent, mode: IslandRenderMode, modifier: Modifier) {
        when (mode) {
            IslandRenderMode.COLLAPSED -> collapsed(event, modifier)
            IslandRenderMode.EXPANDED -> expanded(event, modifier)
            IslandRenderMode.MINIMIZED -> minimized(event, modifier)
        }
    }
}

/** The full renderer set, lowest `order` first; the last one accepts anything. */
fun defaultIslandRenderers(): List<IslandRenderer> = listOf(
    CallIslandRenderer,
    MediaIslandRenderer(),
    TimerIslandRenderer,
    StopwatchIslandRenderer,
    AlarmIslandRenderer,
    ChargingIslandRenderer,
    BatteryIslandRenderer,
    DeviceIslandRenderer,
    NavigationIslandRenderer,
    DownloadIslandRenderer,
    NotificationIslandRenderer,
    SystemIslandRenderer,
    CustomIslandRenderer,
    GenericIslandRenderer,
)

// region notification

private val NotificationIslandRenderer = SimpleIslandRenderer(
    order = 60,
    matches = { it is IslandEvent.Notification },
    collapsed = { event, modifier -> NotificationCollapsed(event, modifier) },
    expanded = { event, modifier -> NotificationExpanded(event, modifier) },
    minimized = { event, modifier -> MinimizedEvent(event, modifier) },
)

@Composable
private fun NotificationCollapsed(event: IslandEvent, modifier: Modifier) {
    val notification = (event as? IslandEvent.Notification)?.notification
    val grouped = (event as? IslandEvent.Notification)?.grouped.orEmpty()
    val accent = accentFor(event)
    IslandCollapsedRow(
        modifier = modifier,
        leading = {
            IslandAppIcon(
                packageName = notification?.packageName,
                contentDescription = stringResource(R.string.a11y_app_icon),
                size = IslandDimensions.appIconSize,
            )
        },
        title = event.title,
        subtitle = event.subtitle,
        trailing = {
            val total = grouped.size + 1
            if (total > 1) {
                IslandChip(text = "$total")
            } else if (notification?.hasProgress == true) {
                IslandText(
                    text = notification.progressFraction?.let { IslandFormat.percent((it * 100).toInt()) } ?: "",
                    style = IslandTypography.caption,
                    color = IslandColors.TextSecondary,
                )
            }
        },
        progress = notification?.progressFraction,
        progressColor = accent,
        indeterminateProgress = notification?.progressIndeterminate == true,
    )
}

@Composable
private fun NotificationExpanded(event: IslandEvent, modifier: Modifier) {
    val context = LocalIslandRender.current
    val notification = (event as? IslandEvent.Notification)?.notification
    val grouped = (event as? IslandEvent.Notification)?.grouped.orEmpty()
    val accent = accentFor(event)

    IslandExpandedCard(
        modifier = modifier,
        header = {
            IslandHeader(
                title = event.title,
                subtitle = notification?.appName ?: event.sourceLabel,
                leading = {
                    IslandAppIcon(
                        packageName = notification?.packageName,
                        contentDescription = stringResource(R.string.a11y_app_icon),
                        size = IslandDimensions.appIconSizeExpanded,
                    )
                },
                trailing = {
                    IslandQueueStrip(count = context.queueCount, focusedIndex = context.queueIndex)
                },
            )
        },
        body = {
            val body = event.subtitle ?: notification?.previewText
            if (!body.isNullOrBlank()) {
                Text(
                    text = body,
                    color = IslandColors.TextSecondary,
                    style = IslandTypography.expandedSubtitle,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (grouped.isNotEmpty()) {
                IslandDivider()
                val groupCount = grouped.size + 1
                IslandSectionLabel(
                    text = pluralStringResource(R.plurals.island_grouped_notifications, groupCount, groupCount),
                )
                grouped.take(3).forEach { sibling ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(IslandSpacing.sm),
                    ) {
                        IslandAppIcon(
                            packageName = sibling.packageName,
                            contentDescription = null,
                            size = IslandDimensions.appIconSize,
                        )
                        Text(
                            text = sibling.title ?: sibling.appName,
                            color = IslandColors.TextPrimary,
                            style = IslandTypography.collapsedTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (!sibling.text.isNullOrBlank()) {
                            Text(
                                text = sibling.text.orEmpty(),
                                color = IslandColors.TextTertiary,
                                style = IslandTypography.caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        controls = { IslandActionRow(actions = event.actions, accent = accent) },
    )
}

// endregion

// region timer

private val TimerIslandRenderer = SimpleIslandRenderer(
    order = 30,
    matches = { it is IslandEvent.Timer },
    collapsed = { event, modifier -> TimerCollapsed(event, modifier) },
    expanded = { event, modifier -> TimerExpanded(event, modifier) },
    minimized = { event, modifier -> MinimizedEvent(event, modifier) },
)

@Composable
private fun TimerCollapsed(event: IslandEvent, modifier: Modifier) {
    val timer = (event as? IslandEvent.Timer)?.timer ?: return
    val context = LocalIslandRender.current
    val remaining = timer.remainingAt(context.nowElapsedMs)
    val accent = if (timer.finished) IslandColors.AccentRed else accentFor(event)
    IslandCollapsedRow(
        modifier = modifier,
        leading = {
            IslandIcon(
                iconKey = IslandIconKey.TIMER,
                contentDescription = null,
                tint = accent,
            )
        },
        title = if (timer.finished) event.title else IslandFormat.clock(remaining),
        subtitle = timer.label,
        titleStyle = IslandTypography.timerMedium,
        progress = timer.remainingFractionAt(context.nowElapsedMs),
        progressColor = accent,
        trailing = {
            if (timer.activeTimerCount > 1) IslandChip(text = "${timer.index + 1}/${timer.activeTimerCount}")
        },
    )
}

@Composable
private fun TimerExpanded(event: IslandEvent, modifier: Modifier) {
    val timer = (event as? IslandEvent.Timer)?.timer ?: return
    val context = LocalIslandRender.current
    val remaining = timer.remainingAt(context.nowElapsedMs)
    val accent = if (timer.finished) IslandColors.AccentRed else accentFor(event)

    IslandExpandedCard(
        modifier = modifier,
        header = {
            IslandHeader(
                title = timer.label ?: event.title,
                subtitle = when {
                    timer.finished -> event.subtitle
                    timer.running -> stringResource(R.string.island_time_remaining, (remaining / 60_000L).toInt().coerceAtLeast(1))
                    else -> stringResource(R.string.island_paused)
                },
                leading = {
                    IslandIcon(
                        iconKey = IslandIconKey.TIMER,
                        contentDescription = null,
                        tint = accent,
                        size = IslandDimensions.iconSizeLarge * 1.4f,
                    )
                },
                trailing = { IslandQueueStrip(context.queueCount, context.queueIndex) },
            )
        },
        body = {
            IslandMetric(
                value = if (timer.finished) event.title else IslandFormat.clock(remaining),
                color = accent,
                style = IslandTypography.timerLarge,
            )
            IslandProgress(
                progress = timer.remainingFractionAt(context.nowElapsedMs),
                modifier = Modifier.fillMaxWidth(),
                height = IslandDimensions.progressHeightExpanded,
                color = accent,
            )
        },
        controls = { IslandActionRow(actions = event.actions, accent = accent) },
    )
}

// endregion

// region stopwatch

private val StopwatchIslandRenderer = SimpleIslandRenderer(
    order = 35,
    matches = { it is IslandEvent.Stopwatch },
    collapsed = { event, modifier -> StopwatchCollapsed(event, modifier) },
    expanded = { event, modifier -> StopwatchExpanded(event, modifier) },
    minimized = { event, modifier -> MinimizedEvent(event, modifier) },
)

@Composable
private fun StopwatchCollapsed(event: IslandEvent, modifier: Modifier) {
    val stopwatch = (event as? IslandEvent.Stopwatch)?.stopwatch ?: return
    val context = LocalIslandRender.current
    val elapsed = stopwatch.elapsedAt(context.nowElapsedMs)
    val accent = accentFor(event)
    IslandCollapsedRow(
        modifier = modifier,
        leading = {
            IslandIcon(iconKey = IslandIconKey.STOPWATCH, contentDescription = null, tint = accent)
        },
        title = IslandFormat.clockWithTenths(elapsed),
        titleStyle = IslandTypography.timerMedium,
        subtitle = if (stopwatch.running) null else stringResource(R.string.island_paused),
        trailing = {
            if (stopwatch.laps.isNotEmpty()) {
                IslandChip(text = stringResource(R.string.stopwatch_lap_number, stopwatch.laps.size))
            }
        },
    )
}

@Composable
private fun StopwatchExpanded(event: IslandEvent, modifier: Modifier) {
    val stopwatch = (event as? IslandEvent.Stopwatch)?.stopwatch ?: return
    val context = LocalIslandRender.current
    val accent = accentFor(event)
    val splits = stopwatch.splits()

    IslandExpandedCard(
        modifier = modifier,
        header = {
            IslandHeader(
                title = event.title,
                subtitle = if (stopwatch.running) null else stringResource(R.string.island_paused),
                leading = {
                    IslandIcon(
                        iconKey = IslandIconKey.STOPWATCH,
                        contentDescription = null,
                        tint = accent,
                        size = IslandDimensions.iconSizeLarge * 1.4f,
                    )
                },
                trailing = { IslandQueueStrip(context.queueCount, context.queueIndex) },
            )
        },
        body = {
            IslandMetric(
                value = IslandFormat.clockWithTenths(stopwatch.elapsedAt(context.nowElapsedMs)),
                color = IslandColors.TextPrimary,
                style = IslandTypography.timerLarge,
            )
            if (splits.isNotEmpty()) {
                IslandDivider()
                splits.takeLast(3).reversed().forEachIndexed { index, split ->
                    val lapNumber = splits.size - index
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IslandSectionLabel(text = stringResource(R.string.stopwatch_lap_number, lapNumber))
                        IslandText(
                            text = IslandFormat.clockWithTenths(split),
                            style = IslandTypography.collapsedTitle,
                            color = IslandColors.TextSecondary,
                        )
                    }
                }
            }
        },
        controls = { IslandActionRow(actions = event.actions, accent = accent) },
    )
}

// endregion

// region call

private val CallIslandRenderer = SimpleIslandRenderer(
    order = 5,
    matches = { it is IslandEvent.Call },
    collapsed = { event, modifier -> CallCollapsed(event, modifier) },
    expanded = { event, modifier -> CallExpanded(event, modifier) },
    minimized = { event, modifier -> MinimizedEvent(event, modifier) },
)

@Composable
private fun CallCollapsed(event: IslandEvent, modifier: Modifier) {
    val call = (event as? IslandEvent.Call)?.call ?: return
    val context = LocalIslandRender.current
    val accent = callAccent(call.state, event)
    val duration = if (call.state == CallState.ACTIVE || call.state == CallState.HOLDING) {
        IslandFormat.clock(call.durationAt(context.nowElapsedMs))
    } else {
        null
    }
    IslandCollapsedRow(
        modifier = modifier,
        leading = {
            IslandIcon(iconKey = event.iconKey, contentDescription = null, tint = accent)
        },
        title = event.title,
        subtitle = duration ?: event.subtitle,
        trailing = {
            if (call.isMuted) {
                IslandChip(text = stringResource(R.string.action_mute))
            }
        },
    )
}

@Composable
private fun CallExpanded(event: IslandEvent, modifier: Modifier) {
    val call = (event as? IslandEvent.Call)?.call ?: return
    val context = LocalIslandRender.current
    val settings = context.settings.calls
    val accent = callAccent(call.state, event)
    val active = call.state == CallState.ACTIVE || call.state == CallState.HOLDING

    IslandExpandedCard(
        modifier = modifier,
        header = {
            IslandHeader(
                title = event.title,
                subtitle = when {
                    !settings.showHandle -> event.subtitle
                    call.handle != null -> call.handle
                    else -> event.subtitle
                },
                leading = {
                    IslandIcon(
                        iconKey = event.iconKey,
                        contentDescription = null,
                        tint = accent,
                        size = IslandDimensions.iconSizeLarge * 1.5f,
                    )
                },
                trailing = { IslandQueueStrip(context.queueCount, context.queueIndex) },
            )
        },
        body = {
            if (active) {
                IslandMetric(
                    value = IslandFormat.clock(call.durationAt(context.nowElapsedMs)),
                    color = IslandColors.TextPrimary,
                    style = IslandTypography.timerMedium,
                )
            }
            if (call.isMuted) {
                IslandSectionLabel(text = stringResource(R.string.action_mute))
            }
        },
        controls = {
            if (settings.showControls) IslandActionRow(actions = event.actions, accent = accent)
        },
    )
}

@Composable
private fun callAccent(state: CallState, event: IslandEvent): Color = when (state) {
    CallState.INCOMING -> IslandColors.AccentGreen
    CallState.ENDED -> IslandColors.AccentRed
    else -> accentFor(event)
}

// endregion

// region charging & battery

private val ChargingIslandRenderer = SimpleIslandRenderer(
    order = 40,
    matches = { it is IslandEvent.Charging },
    collapsed = { event, modifier -> ChargingCollapsed(event, modifier) },
    expanded = { event, modifier -> ChargingExpanded(event, modifier) },
    minimized = { event, modifier -> MinimizedEvent(event, modifier) },
)

@Composable
private fun ChargingCollapsed(event: IslandEvent, modifier: Modifier) {
    val charging = (event as? IslandEvent.Charging)?.charging ?: return
    val context = LocalIslandRender.current
    val accent = IslandColors.AccentGreen
    val levelLabel = stringResource(R.string.island_battery_percent, charging.levelPercent)
    val watts = if (context.settings.battery.showWattage) IslandFormat.wattage(charging.wattage) else null
    val batteryDescription = stringResource(R.string.a11y_battery_level, charging.levelPercent)

    IslandCollapsedRow(
        modifier = modifier.semantics { contentDescription = batteryDescription },
        leading = {
            ChargingGlyph(
                animate = context.settings.battery.chargingAnimation && charging.isCharging,
                reduceMotion = context.reduceMotion,
                tint = accent,
            )
        },
        title = levelLabel,
        subtitle = watts ?: event.subtitle,
        titleStyle = IslandTypography.timerMedium,
        progress = charging.levelPercent / 100f,
        progressColor = accent,
    )
}

@Composable
private fun ChargingExpanded(event: IslandEvent, modifier: Modifier) {
    val charging = (event as? IslandEvent.Charging)?.charging ?: return
    val context = LocalIslandRender.current
    val accent = IslandColors.AccentGreen
    val showWatts = context.settings.battery.showWattage
    val wirelessLabel =
        if (charging.plugged == PlugType.WIRELESS) stringResource(R.string.island_charging_wireless) else null
    val chips = listOfNotNull(
        if (showWatts) IslandFormat.wattage(charging.wattage) else null,
        IslandFormat.voltage(charging.voltageMv),
        IslandFormat.current(charging.currentMicroAmps),
        IslandFormat.temperature(charging.temperatureTenthsCelsius),
        wirelessLabel,
    )

    IslandExpandedCard(
        modifier = modifier,
        header = {
            IslandHeader(
                title = event.title,
                subtitle = event.subtitle,
                leading = {
                    ChargingGlyph(
                        animate = context.settings.battery.chargingAnimation && charging.isCharging,
                        reduceMotion = context.reduceMotion,
                        tint = accent,
                        size = IslandDimensions.iconSizeLarge * 1.5f,
                    )
                },
                trailing = { IslandQueueStrip(context.queueCount, context.queueIndex) },
            )
        },
        body = {
            IslandMetric(
                value = stringResource(R.string.island_battery_percent, charging.levelPercent),
                caption = event.subtitle,
                color = accent,
                style = IslandTypography.timerLarge,
            )
            IslandProgress(
                progress = charging.levelPercent / 100f,
                modifier = Modifier.fillMaxWidth(),
                height = IslandDimensions.progressHeightExpanded,
                color = accent,
            )
            if (chips.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(IslandSpacing.sm, Alignment.CenterHorizontally),
                ) {
                    chips.take(4).forEach { chip -> IslandChip(text = chip) }
                }
            }
        },
        controls = { IslandActionRow(actions = event.actions, accent = accent) },
    )
}

/** A charging bolt that breathes. Static when animations are reduced or charging is done. */
@Composable
private fun ChargingGlyph(
    animate: Boolean,
    reduceMotion: Boolean,
    tint: Color,
    size: Dp = IslandDimensions.iconSizeLarge,
) {
    val transition = rememberInfiniteTransition(label = "island-charge")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(IslandMotion.chargePulsePeriodMs),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "charge-pulse",
    )
    val alpha = if (!animate || reduceMotion) 1f else pulse
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        IslandIcon(
            iconKey = IslandIconKey.BATTERY_CHARGING,
            contentDescription = null,
            tint = tint.copy(alpha = alpha),
            size = size,
        )
    }
}

private val BatteryIslandRenderer = SimpleIslandRenderer(
    order = 45,
    matches = { it is IslandEvent.Battery },
    collapsed = { event, modifier -> BatteryCollapsed(event, modifier) },
    expanded = { event, modifier -> BatteryExpanded(event, modifier) },
    minimized = { event, modifier -> MinimizedEvent(event, modifier) },
)

@Composable
private fun BatteryCollapsed(event: IslandEvent, modifier: Modifier) {
    val battery = (event as? IslandEvent.Battery)?.battery ?: return
    val accent = batteryAccent(battery.isLow, battery.isCritical, battery.isFull)
    IslandCollapsedRow(
        modifier = modifier,
        leading = {
            IslandIcon(iconKey = event.iconKey, contentDescription = null, tint = accent)
        },
        title = stringResource(R.string.island_battery_percent, battery.levelPercent),
        subtitle = event.subtitle,
        titleStyle = IslandTypography.timerMedium,
        progress = battery.levelPercent / 100f,
        progressColor = accent,
    )
}

@Composable
private fun BatteryExpanded(event: IslandEvent, modifier: Modifier) {
    val battery = (event as? IslandEvent.Battery)?.battery ?: return
    val context = LocalIslandRender.current
    val accent = batteryAccent(battery.isLow, battery.isCritical, battery.isFull)
    val batteryDescription = stringResource(R.string.a11y_battery_level, battery.levelPercent)

    IslandExpandedCard(
        modifier = modifier.semantics { contentDescription = batteryDescription },
        header = {
            IslandHeader(
                title = event.title,
                subtitle = event.subtitle,
                leading = {
                    IslandIcon(
                        iconKey = event.iconKey,
                        contentDescription = null,
                        tint = accent,
                        size = IslandDimensions.iconSizeLarge * 1.5f,
                    )
                },
                trailing = { IslandQueueStrip(context.queueCount, context.queueIndex) },
            )
        },
        body = {
            IslandMetric(
                value = stringResource(R.string.island_battery_percent, battery.levelPercent),
                color = accent,
                style = IslandTypography.timerLarge,
            )
            IslandProgress(
                progress = battery.levelPercent / 100f,
                modifier = Modifier.fillMaxWidth(),
                height = IslandDimensions.progressHeightExpanded,
                color = accent,
            )
        },
        controls = { IslandActionRow(actions = event.actions, accent = accent) },
    )
}

private fun batteryAccent(low: Boolean, critical: Boolean, full: Boolean): Color = when {
    critical || low -> IslandColors.AccentRed
    full -> IslandColors.AccentGreen
    else -> IslandColors.AccentDefault
}

// endregion

// region connected devices

private val DeviceIslandRenderer = SimpleIslandRenderer(
    order = 50,
    matches = { it is IslandEvent.Bluetooth },
    collapsed = { event, modifier -> DeviceCollapsed(event, modifier) },
    expanded = { event, modifier -> DeviceExpanded(event, modifier) },
    minimized = { event, modifier -> MinimizedEvent(event, modifier) },
)

@Composable
private fun DeviceCollapsed(event: IslandEvent, modifier: Modifier) {
    val accent = accentFor(event)
    IslandCollapsedRow(
        modifier = modifier,
        leading = { IslandIcon(iconKey = event.iconKey, contentDescription = null, tint = accent) },
        title = event.title,
        subtitle = event.subtitle,
    )
}

@Composable
private fun DeviceExpanded(event: IslandEvent, modifier: Modifier) {
    val device = (event as? IslandEvent.Bluetooth)?.device
    val context = LocalIslandRender.current
    val accent = accentFor(event)
    // The MAC address is deliberately never rendered: it is a stable device identifier.
    IslandExpandedCard(
        modifier = modifier,
        header = {
            IslandHeader(
                title = event.title,
                subtitle = event.subtitle,
                leading = {
                    IslandIcon(
                        iconKey = event.iconKey,
                        contentDescription = null,
                        tint = accent,
                        size = IslandDimensions.iconSizeLarge * 1.5f,
                    )
                },
                trailing = { IslandQueueStrip(context.queueCount, context.queueIndex) },
            )
        },
        body = {
            if (device?.name != null) {
                IslandText(text = device.name, style = IslandTypography.expandedSubtitle)
            }
            if (device != null) {
                IslandSectionLabel(text = deviceKindLabel(device.kind))
            }
        },
        controls = { IslandActionRow(actions = event.actions, accent = accent) },
    )
}

@Composable
private fun deviceKindLabel(kind: ConnectedDeviceKind): String = when (kind) {
    ConnectedDeviceKind.WIRED_HEADSET -> stringResource(R.string.device_kind_headset)
    ConnectedDeviceKind.BLUETOOTH_AUDIO -> stringResource(R.string.device_kind_bluetooth_audio)
    ConnectedDeviceKind.WATCH -> stringResource(R.string.device_kind_watch)
    ConnectedDeviceKind.CAR -> stringResource(R.string.device_kind_car)
    ConnectedDeviceKind.SPEAKER -> stringResource(R.string.device_kind_speaker)
    ConnectedDeviceKind.OTHER -> stringResource(R.string.device_kind_other)
}

// endregion

// region alarm

private val AlarmIslandRenderer = SimpleIslandRenderer(
    order = 20,
    matches = { it is IslandEvent.Alarm },
    collapsed = { event, modifier -> AlarmCollapsed(event, modifier) },
    expanded = { event, modifier -> AlarmExpanded(event, modifier) },
    minimized = { event, modifier -> MinimizedEvent(event, modifier) },
)

@Composable
private fun AlarmCollapsed(event: IslandEvent, modifier: Modifier) {
    val alarm = (event as? IslandEvent.Alarm)?.alarm
    val accent = if (alarm?.isRinging == true) IslandColors.AccentAmber else accentFor(event)
    IslandCollapsedRow(
        modifier = modifier,
        leading = { IslandIcon(iconKey = IslandIconKey.ALARM, contentDescription = null, tint = accent) },
        title = event.title,
        subtitle = event.subtitle,
    )
}

@Composable
private fun AlarmExpanded(event: IslandEvent, modifier: Modifier) {
    val alarm = (event as? IslandEvent.Alarm)?.alarm ?: return
    val context = LocalIslandRender.current
    val accent = if (alarm.isRinging) IslandColors.AccentAmber else accentFor(event)
    val minutes = alarm.minutesUntil(context.nowElapsedMs)

    IslandExpandedCard(
        modifier = modifier,
        header = {
            IslandHeader(
                title = event.title,
                subtitle = alarm.label ?: event.subtitle,
                leading = {
                    IslandIcon(
                        iconKey = IslandIconKey.ALARM,
                        contentDescription = null,
                        tint = accent,
                        size = IslandDimensions.iconSizeLarge * 1.5f,
                    )
                },
                trailing = { IslandQueueStrip(context.queueCount, context.queueIndex) },
            )
        },
        body = {
            if (!alarm.isRinging && minutes > 0) {
                IslandMetric(
                    value = stringResource(R.string.island_alarm_in, minutes.toInt()),
                    color = IslandColors.TextPrimary,
                    style = IslandTypography.timerMedium,
                )
            }
        },
        controls = { IslandActionRow(actions = event.actions, accent = accent) },
    )
}

// endregion

// region navigation

private val NavigationIslandRenderer = SimpleIslandRenderer(
    order = 25,
    matches = { it is IslandEvent.Navigation },
    collapsed = { event, modifier -> NavigationCollapsed(event, modifier) },
    expanded = { event, modifier -> NavigationExpanded(event, modifier) },
    minimized = { event, modifier -> MinimizedEvent(event, modifier) },
)

@Composable
private fun NavigationCollapsed(event: IslandEvent, modifier: Modifier) {
    val navigation = (event as? IslandEvent.Navigation)?.navigation ?: return
    val accent = IslandColors.AccentDefault
    IslandCollapsedRow(
        modifier = modifier,
        leading = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(IslandDimensions.iconSizeLarge)) {
                Icon(
                    imageVector = maneuverIcon(navigation.maneuver),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(IslandDimensions.iconSize),
                )
            }
        },
        title = navigation.distanceMeters?.let { distanceLabel(it) } ?: event.title,
        subtitle = navigation.streetName ?: event.subtitle,
    )
}

@Composable
private fun NavigationExpanded(event: IslandEvent, modifier: Modifier) {
    val navigation = (event as? IslandEvent.Navigation)?.navigation ?: return
    val context = LocalIslandRender.current
    val accent = IslandColors.AccentDefault

    IslandExpandedCard(
        modifier = modifier,
        header = {
            IslandHeader(
                title = maneuverLabel(navigation.maneuver),
                subtitle = navigation.streetName ?: navigation.provider ?: event.subtitle,
                leading = {
                    Icon(
                        imageVector = maneuverIcon(navigation.maneuver),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(IslandDimensions.iconSizeLarge * 1.4f),
                    )
                },
                trailing = { IslandQueueStrip(context.queueCount, context.queueIndex) },
            )
        },
        body = {
            val distance = navigation.distanceMeters?.let { distanceLabel(it) }
            if (distance != null) {
                IslandMetric(value = distance, color = accent, style = IslandTypography.timerLarge)
            }
            val etaLabel = navigation.etaMinutes?.let { IslandFormat.eta(it) }
            val remainingLabel = navigation.remainingDistanceMeters?.let { distanceLabel(it) }
            val chips = listOfNotNull(etaLabel, remainingLabel)
            if (chips.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(IslandSpacing.sm, Alignment.CenterHorizontally),
                ) {
                    chips.take(2).forEach { chip -> IslandChip(text = chip) }
                }
            }
        },
        controls = { IslandActionRow(actions = event.actions, accent = accent) },
    )
}

private fun maneuverIcon(kind: ManeuverKind): ImageVector = when (kind) {
    ManeuverKind.STRAIGHT, ManeuverKind.MERGE, ManeuverKind.FORK -> Icons.Rounded.ArrowUpward
    ManeuverKind.LEFT, ManeuverKind.SLIGHT_LEFT, ManeuverKind.SHARP_LEFT -> Icons.Rounded.ArrowBack
    ManeuverKind.RIGHT, ManeuverKind.SLIGHT_RIGHT, ManeuverKind.SHARP_RIGHT, ManeuverKind.EXIT ->
        Icons.Rounded.ArrowForward

    ManeuverKind.U_TURN, ManeuverKind.ROUNDABOUT -> Icons.Rounded.Refresh
    ManeuverKind.ARRIVE -> Icons.Rounded.Flag
    ManeuverKind.UNKNOWN -> Icons.Rounded.Navigation
}

@Composable
private fun maneuverLabel(kind: ManeuverKind): String = when (kind) {
    ManeuverKind.STRAIGHT -> stringResource(R.string.maneuver_straight)
    ManeuverKind.LEFT -> stringResource(R.string.maneuver_left)
    ManeuverKind.RIGHT -> stringResource(R.string.maneuver_right)
    ManeuverKind.SLIGHT_LEFT -> stringResource(R.string.maneuver_slight_left)
    ManeuverKind.SLIGHT_RIGHT -> stringResource(R.string.maneuver_slight_right)
    ManeuverKind.SHARP_LEFT -> stringResource(R.string.maneuver_sharp_left)
    ManeuverKind.SHARP_RIGHT -> stringResource(R.string.maneuver_sharp_right)
    ManeuverKind.U_TURN -> stringResource(R.string.maneuver_u_turn)
    ManeuverKind.MERGE -> stringResource(R.string.maneuver_merge)
    ManeuverKind.FORK -> stringResource(R.string.maneuver_fork)
    ManeuverKind.ROUNDABOUT -> stringResource(R.string.maneuver_roundabout)
    ManeuverKind.EXIT -> stringResource(R.string.maneuver_exit)
    ManeuverKind.ARRIVE -> stringResource(R.string.maneuver_arrive)
    ManeuverKind.UNKNOWN -> stringResource(R.string.maneuver_unknown)
}

@Composable
private fun distanceLabel(meters: Int): String =
    if (meters < 1000) {
        stringResource(R.string.distance_meters, meters)
    } else {
        stringResource(R.string.distance_kilometers, meters / 1000f)
    }

// endregion

// region download

private val DownloadIslandRenderer = SimpleIslandRenderer(
    order = 55,
    matches = { it is IslandEvent.Download },
    collapsed = { event, modifier -> DownloadCollapsed(event, modifier) },
    expanded = { event, modifier -> DownloadExpanded(event, modifier) },
    minimized = { event, modifier -> MinimizedEvent(event, modifier) },
)

@Composable
private fun DownloadCollapsed(event: IslandEvent, modifier: Modifier) {
    val download = (event as? IslandEvent.Download)?.download ?: return
    val accent = accentFor(event)
    IslandCollapsedRow(
        modifier = modifier,
        leading = { IslandIcon(iconKey = IslandIconKey.DOWNLOAD, contentDescription = null, tint = accent) },
        title = download.fileName ?: event.title,
        trailing = {
            download.percent?.let {
                IslandText(
                    text = IslandFormat.percent(it),
                    style = IslandTypography.caption,
                    color = IslandColors.TextSecondary,
                )
            }
        },
        progress = download.fraction,
        progressColor = accent,
        indeterminateProgress = download.indeterminate && !download.completed,
    )
}

@Composable
private fun DownloadExpanded(event: IslandEvent, modifier: Modifier) {
    val download = (event as? IslandEvent.Download)?.download ?: return
    val context = LocalIslandRender.current
    val accent = accentFor(event)
    val downloadedLabel = IslandFormat.bytes(download.downloadedBytes)
    val totalLabel = IslandFormat.bytes(download.totalBytes)
    val sizes = listOfNotNull(downloadedLabel, totalLabel)

    IslandExpandedCard(
        modifier = modifier,
        header = {
            IslandHeader(
                title = download.fileName ?: event.title,
                subtitle = event.subtitle ?: download.percent?.let { IslandFormat.percent(it) },
                leading = {
                    IslandIcon(
                        iconKey = IslandIconKey.DOWNLOAD,
                        contentDescription = null,
                        tint = accent,
                        size = IslandDimensions.iconSizeLarge * 1.4f,
                    )
                },
                trailing = { IslandQueueStrip(context.queueCount, context.queueIndex) },
            )
        },
        body = {
            IslandProgress(
                progress = if (download.indeterminate) null else download.fraction,
                modifier = Modifier.fillMaxWidth(),
                height = IslandDimensions.progressHeightExpanded,
                color = accent,
            )
            val detail = listOfNotNull(
                if (sizes.size == 2) stringResource(R.string.download_progress, sizes[0], sizes[1]) else sizes.firstOrNull(),
                IslandFormat.speed(download.bytesPerSecond),
            )
            if (detail.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(IslandSpacing.sm, Alignment.CenterHorizontally),
                ) {
                    detail.forEach { item -> IslandChip(text = item) }
                }
            }
        },
        controls = { IslandActionRow(actions = event.actions, accent = accent) },
    )
}

// endregion

// region system, custom & generic

private val SystemIslandRenderer = SimpleIslandRenderer(
    order = 70,
    matches = { it is IslandEvent.System },
    collapsed = { event, modifier -> PlainCollapsed(event, modifier) },
    expanded = { event, modifier -> PlainExpanded(event, modifier) },
    minimized = { event, modifier -> MinimizedEvent(event, modifier) },
)

private val CustomIslandRenderer = SimpleIslandRenderer(
    order = 75,
    matches = { it is IslandEvent.Custom },
    collapsed = { event, modifier -> PlainCollapsed(event, modifier) },
    expanded = { event, modifier -> CustomExpanded(event, modifier) },
    minimized = { event, modifier -> MinimizedEvent(event, modifier) },
)

/** Last resort: anything without a dedicated renderer still gets a clean, correct island. */
private val GenericIslandRenderer = SimpleIslandRenderer(
    order = 1000,
    matches = { true },
    collapsed = { event, modifier -> PlainCollapsed(event, modifier) },
    expanded = { event, modifier -> PlainExpanded(event, modifier) },
    minimized = { event, modifier -> MinimizedEvent(event, modifier) },
)

@Composable
private fun PlainCollapsed(event: IslandEvent, modifier: Modifier) {
    val accent = accentFor(event)
    IslandCollapsedRow(
        modifier = modifier,
        leading = { IslandIcon(iconKey = event.iconKey, contentDescription = null, tint = accent) },
        title = event.title,
        subtitle = event.subtitle,
        progress = event.progress,
        progressColor = accent,
    )
}

@Composable
private fun PlainExpanded(event: IslandEvent, modifier: Modifier) {
    val context = LocalIslandRender.current
    val accent = accentFor(event)
    val detail = when (event) {
        is IslandEvent.System -> event.system.detail
        is IslandEvent.Custom -> event.custom.category
        else -> null
    }

    IslandExpandedCard(
        modifier = modifier,
        header = {
            IslandHeader(
                title = event.title,
                subtitle = event.subtitle,
                leading = {
                    IslandIcon(
                        iconKey = event.iconKey,
                        contentDescription = null,
                        tint = accent,
                        size = IslandDimensions.iconSizeLarge * 1.4f,
                    )
                },
                trailing = { IslandQueueStrip(context.queueCount, context.queueIndex) },
            )
        },
        body = {
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    color = IslandColors.TextSecondary,
                    style = IslandTypography.expandedSubtitle,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            event.progress?.let {
                IslandProgress(
                    progress = it,
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                )
            }
        },
        controls = { IslandActionRow(actions = event.actions, accent = accent) },
    )
}

@Composable
private fun CustomExpanded(event: IslandEvent, modifier: Modifier) {
    val custom = (event as? IslandEvent.Custom)?.custom
    val extras = custom?.extras.orEmpty()
    if (extras.isEmpty()) {
        PlainExpanded(event, modifier)
        return
    }
    val context = LocalIslandRender.current
    val accent = accentFor(event)

    IslandExpandedCard(
        modifier = modifier,
        header = {
            IslandHeader(
                title = event.title,
                subtitle = custom?.category ?: event.subtitle,
                leading = {
                    IslandIcon(
                        iconKey = event.iconKey,
                        contentDescription = null,
                        tint = accent,
                        size = IslandDimensions.iconSizeLarge * 1.4f,
                    )
                },
                trailing = { IslandQueueStrip(context.queueCount, context.queueIndex) },
            )
        },
        body = {
            extras.entries.take(3).forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IslandSectionLabel(text = key)
                    IslandText(text = value, color = IslandColors.TextSecondary)
                }
            }
        },
        controls = { IslandActionRow(actions = event.actions, accent = accent) },
    )
}

/** Landscape / minimized: an icon and nothing else — zero text, zero layout thrash. */
@Composable
private fun MinimizedEvent(event: IslandEvent, modifier: Modifier) {
    val accent = accentFor(event)
    val label = stringResource(R.string.a11y_island_collapsed)
    Box(
        modifier = modifier
            .padding(2.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IslandIcon(
                iconKey = event.iconKey,
                contentDescription = null,
                tint = accent,
                size = IslandDimensions.iconSize,
            )
            event.progress?.let {
                IslandProgress(
                    progress = it,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(width = IslandDimensions.iconSize, height = IslandDimensions.progressHeight),
                    color = accent,
                )
            }
        }
    }
}

// endregion

// region accent helpers

/** Accent for an event: user accent → event accent → readable default. */
@Composable
private fun accentFor(event: IslandEvent): Color {
    val context = LocalIslandRender.current
    return resolveAccent(context.appearance.accentArgb, event.accentArgb)
}

// endregion
