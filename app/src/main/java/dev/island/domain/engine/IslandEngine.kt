package dev.island.domain.engine

import dev.island.core.logging.IslandLogger
import dev.island.core.logging.Redaction
import dev.island.domain.model.DeviceContext
import dev.island.domain.model.DisabledReason
import dev.island.domain.model.HistoryAction
import dev.island.domain.model.HistoryEntry
import dev.island.domain.model.IslandAction
import dev.island.domain.model.IslandActionKind
import dev.island.domain.model.IslandEvent
import dev.island.domain.model.IslandPhase
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.IslandUiState
import dev.island.domain.model.LandscapeBehavior
import dev.island.domain.repository.DeviceStateRepository
import dev.island.domain.repository.EventHistoryRepository
import dev.island.domain.repository.IslandActionHandler
import dev.island.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The brain of Island.
 *
 * Responsibilities:
 * - accept events from every source and normalise them (priority, privacy, persistence, expiry);
 * - maintain one ordered [EventStack] with a single focused event (no five-way UI fight);
 * - drive [IslandStateMachine] and publish an immutable [IslandUiState];
 * - coalesce chatty bursts so an animation is played once, not four times;
 * - enforce device gating (screen off, DND, lock screen, landscape) and permission gating.
 *
 * Everything mutates through a single serialized command channel, so no locking is needed and
 * the engine is deterministic under test (see IslandEngineScenariosTest).
 */
class IslandEngine(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val deviceStateRepository: DeviceStateRepository,
    private val clock: IslandClock,
    private val logger: IslandLogger,
    private val historyRepository: EventHistoryRepository? = null,
    private val actionHandler: IslandActionHandler? = null,
    private val coalesceWindowMs: Long = DEFAULT_COALESCE_WINDOW_MS,
    private val animationFallbackMs: Long = DEFAULT_ANIMATION_FALLBACK_MS,
) {

    private sealed interface Command {
        data class Arrived(val event: IslandEvent) : Command
        data class Removed(val eventId: String, val byUser: Boolean = false) : Command
        data object Cleared : Command
        data class SettingsChanged(val settings: IslandSettings) : Command
        data class DeviceChanged(val context: DeviceContext) : Command
        data class EnableChanged(val enabled: Boolean, val reason: DisabledReason?) : Command
        data object Expand : Command
        data object Collapse : Command
        data object InteractionStart : Command
        data object InteractionEnd : Command
        data object AnimationDone : Command
        data class CycleFocus(val delta: Int) : Command
        data object DismissFocused : Command
        data object Pin : Command
        data object Unpin : Command
        data class CollapseTimeout(val eventId: String, val intro: Boolean) : Command
        data class RemovalTimeout(val eventId: String) : Command
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val _uiState = MutableStateFlow(IslandUiState())
    val uiState: StateFlow<IslandUiState> = _uiState.asStateFlow()

    private var stack: EventStack = EventStack.Empty
    private var settings: IslandSettings = IslandSettings.Default
    private var device: DeviceContext = DeviceContext()
    private var phase: IslandPhase = IslandPhase.DISABLED
    private var userEnabled: Boolean = false
    private var externalDisabledReason: DisabledReason? = null
    private var expandedByUser: Boolean = false
    private var pinnedByUser: Boolean = false
    private var loopJob: Job? = null
    private var animationFallbackJob: Job? = null

    /** eventId → (collapse job, removal job). Cancelled whenever the event leaves the stack. */
    private val timeoutJobs = HashMap<String, Pair<Job?, Job?>>()

    /** coalesceKey → monotonic time the user dismissed it, so it cannot immediately reappear. */
    private val userDismissals = HashMap<String, Long>()

    /** Keeps the newest settings snapshot available synchronously for the overlay renderer. */
    val currentSettings: IslandSettings get() = settings

    val activeEventCount: Int get() = stack.events.size

    // region public API

    /** Idempotent. Called by the overlay service and by the app when previewing the island. */
    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { runLoop() }
        logger.i(TAG, "engine started")
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        animationFallbackJob?.cancel()
        timeoutJobs.values.forEach { (collapse, removal) -> collapse?.cancel(); removal?.cancel() }
        timeoutJobs.clear()
        logger.i(TAG, "engine stopped")
    }

    fun submit(event: IslandEvent) {
        commands.trySend(Command.Arrived(event))
    }

    fun remove(eventId: String) {
        commands.trySend(Command.Removed(eventId))
    }

    /** Source-driven removal, e.g. a notification the user cleared in the shade. */
    fun removeByCoalesceKey(key: String) {
        val id = stack.events.firstOrNull { it.coalesceKey == key }?.id ?: return
        commands.trySend(Command.Removed(id))
    }

    fun clear() {
        commands.trySend(Command.Cleared)
    }

    fun expand() {
        commands.trySend(Command.Expand)
    }

    fun collapse() {
        commands.trySend(Command.Collapse)
    }

    fun toggle() {
        if (IslandStateMachine.isExpandedPhase(phase) || expandedByUser) collapse() else expand()
    }

    fun beginInteraction() {
        commands.trySend(Command.InteractionStart)
    }

    fun endInteraction() {
        commands.trySend(Command.InteractionEnd)
    }

    /** Called by the renderer when an expand/collapse animation finished. */
    fun onAnimationCompleted() {
        commands.trySend(Command.AnimationDone)
    }

    fun cycleEvent(delta: Int) {
        commands.trySend(Command.CycleFocus(delta))
    }

    fun dismissFocused() {
        commands.trySend(Command.DismissFocused)
    }

    fun pinFocused() {
        commands.trySend(Command.Pin)
    }

    fun unpinFocused() {
        commands.trySend(Command.Unpin)
    }

    /**
     * Called by the service/controller whenever Island is turned on or off, or when a required
     * capability disappears (overlay permission revoked, service stopped…).
     */
    fun setEnabled(enabled: Boolean, reason: DisabledReason? = null) {
        commands.trySend(Command.EnableChanged(enabled, reason))
    }

    /** Runs an island action; DISMISS is handled locally, everything else is delegated. */
    fun performAction(action: IslandAction) {
        val event = stack.focused ?: return
        recordHistory(event, HistoryAction.ACTION_TAPPED)
        when (action.kind) {
            IslandActionKind.DISMISS -> dismissEvent(event.id)
            else -> scope.launch {
                runCatching { actionHandler?.handle(action, event) }
                    .onFailure { logger.w(TAG, "action failed: ${action.kind}", it) }
            }
        }
    }

    /** Dismiss by id (used by swipe-up and by the queue UI). */
    fun dismissEvent(eventId: String) {
        val event = stack.events.firstOrNull { it.id == eventId } ?: return
        userDismissals[event.coalesceKey] = clock.elapsedMs()
        recordHistory(event, HistoryAction.DISMISSED)
        commands.trySend(Command.Removed(eventId, byUser = true))
    }

    // endregion

    // region command loop

    private suspend fun runLoop() {
        coroutineScope {
            launch {
                runCatching { settingsRepository.settings.collect { commands.send(Command.SettingsChanged(it)) } }
                    .onFailure { logger.e(TAG, "settings collection failed", it) }
            }
            launch {
                runCatching { deviceStateRepository.deviceContext.collect { commands.send(Command.DeviceChanged(it)) } }
                    .onFailure { logger.e(TAG, "device state collection failed", it) }
            }
            while (isActive) {
                val first = commands.receive()
                handleBatch(drain(first))
            }
        }
    }

    /**
     * Event debouncing. A burst of updates to already-visible events (progress notifications,
     * media position ticks) is aggregated for [coalesceWindowMs] and applied as one change, so
     * the island animates once. Anything else is applied immediately with zero added latency.
     */
    private suspend fun drain(first: Command): List<Command> {
        val batch = ArrayList<Command>(4)
        batch.add(first)

        val window = if (shouldCoalesce(batch)) coalesceWindowMs else 0L
        if (window <= 0L) {
            while (true) {
                val next = commands.tryReceive().getOrNull() ?: break
                batch.add(next)
            }
            return batch
        }

        val deadline = clock.elapsedMs() + window
        while (true) {
            val remaining = deadline - clock.elapsedMs()
            if (remaining <= 0L) break
            val next = withTimeoutOrNull(remaining) { commands.receive() } ?: break
            batch.add(next)
            if (!shouldCoalesce(batch)) break
        }
        return batch
    }

    /** Only wait when every command in the batch merely refreshes something already on screen. */
    private fun shouldCoalesce(batch: List<Command>): Boolean {
        if (coalesceWindowMs <= 0L) return false
        return batch.all { command ->
            command is Command.Arrived && stack.events.any { it.coalesceKey == command.event.coalesceKey }
        }
    }

    private fun handleBatch(batch: List<Command>) {
        batch.forEach { command ->
            runCatching { handle(command) }.onFailure { logger.e(TAG, "command failed: $command", it) }
        }
        publish()
    }

    private fun handle(command: Command) {
        when (command) {
            is Command.Arrived -> onArrived(command.event)
            is Command.Removed -> onRemoved(command.eventId, userDismissed = command.byUser)
            Command.Cleared -> onCleared()
            is Command.SettingsChanged -> onSettingsChanged(command.settings)
            is Command.DeviceChanged -> onDeviceChanged(command.context)
            is Command.EnableChanged -> onEnableChanged(command.enabled, command.reason)
            Command.Expand -> onExpand()
            Command.Collapse -> onCollapse()
            Command.InteractionStart -> onInteractionStart()
            Command.InteractionEnd -> onInteractionEnd()
            Command.AnimationDone -> onAnimationDone()
            is Command.CycleFocus -> onCycleFocus(command.delta)
            Command.DismissFocused -> stack.focused?.let { dismissEvent(it.id) }
            Command.Pin -> onPin()
            Command.Unpin -> onUnpin()
            is Command.CollapseTimeout -> onCollapseTimeout(command.eventId, command.intro)
            is Command.RemovalTimeout -> onRemovalTimeout(command.eventId)
        }
    }

    // endregion

    // region command handlers

    private fun onArrived(raw: IslandEvent) {
        val suppression = computeSuppression()
        if (suppression != null) {
            logger.d(TAG, "dropped ${raw.type} (suppressed: $suppression)")
            recordHistory(raw, HistoryAction.SUPPRESSED)
            return
        }

        if (isDismissalSnoozed(raw)) {
            logger.d(TAG, "dropped ${raw.type} (recently dismissed by user)")
            return
        }

        val resolvedPriority = PriorityResolver.resolve(raw, settings)
        if (!PriorityResolver.shouldShow(raw, resolvedPriority, settings, device.doNotDisturb)) {
            logger.d(TAG, "filtered ${raw.type} priority=$resolvedPriority pkg=${Redaction.safePackage(raw.sourcePackage)}")
            recordHistory(raw, HistoryAction.SUPPRESSED)
            return
        }

        val policy = ExpirationPolicy.policyFor(raw, settings)
        val persistent = raw.persistent || policy.isPersistent

        val masked = PrivacyMasker.mask(raw, settings, device.screenLocked)
        val event = masked.withMeta(
            masked.meta.copy(
                priority = resolvedPriority,
                persistent = persistent,
                expiresAt = policy.removeAfterMs?.let { raw.meta.createdAt + it },
            ),
        )

        val result = EventQueue.reduce(stack, QueueCommand.Upsert(event))
        stack = result.stack
        scheduleTimeouts(event, policy, isUpdate = result.wasUpdate)

        phase = IslandStateMachine.next(
            phase,
            IslandTrigger.EventArrived(
                priority = resolvedPriority,
                persistent = persistent,
                isUpdate = result.wasUpdate,
                focusChanged = result.focusChanged,
            ),
            transitionContext(),
        )
        armAnimationFallback()

        if (!result.wasUpdate) {
            logger.i(
                TAG,
                "event ${event.type} id=${event.id} priority=$resolvedPriority " +
                    "persistent=$persistent focus=${result.focusChanged} phase=$phase " +
                    "pkg=${Redaction.safePackage(event.sourcePackage)}",
            )
            recordHistory(event, HistoryAction.SHOWN)
        } else {
            logger.v(TAG, "event ${event.type} updated (no re-animation)")
        }
    }

    private fun onRemoved(eventId: String, userDismissed: Boolean) {
        val event = stack.events.firstOrNull { it.id == eventId } ?: return
        cancelTimeouts(eventId)
        val result = EventQueue.reduce(stack, QueueCommand.Remove(eventId))
        stack = result.stack
        phase = IslandStateMachine.next(
            phase,
            if (stack.hasEvents) IslandTrigger.EventRemoved(stack.events.size) else IslandTrigger.QueueCleared,
            transitionContext(),
        )
        armAnimationFallback()
        if (!userDismissed) recordHistory(event, HistoryAction.EXPIRED)
        logger.d(TAG, "event removed id=$eventId remaining=${stack.events.size} phase=$phase")
    }

    private fun onCleared() {
        timeoutJobs.keys.toList().forEach { cancelTimeouts(it) }
        stack = EventStack.Empty
        userDismissals.clear()
        expandedByUser = false
        pinnedByUser = false
        phase = IslandStateMachine.next(phase, IslandTrigger.QueueCleared, transitionContext())
        armAnimationFallback()
        logger.i(TAG, "queue cleared")
    }

    private fun onSettingsChanged(next: IslandSettings) {
        val previous = settings
        settings = next
        if (previous == next) return

        // Re-gate everything already on screen: a privacy or per-app change applies immediately.
        if (previous.notifications != next.notifications || previous.privacy != next.privacy ||
            previous.calls != next.calls
        ) {
            stack = stack.copy(events = stack.events.map { event ->
                PrivacyMasker.mask(event, next, device.screenLocked)
            })
        }

        // Events the user just turned off leave the island right away.
        val nowDisallowed = stack.events.filter { event ->
            val priority = PriorityResolver.resolve(event, next)
            !PriorityResolver.shouldShow(event, priority, next, device.doNotDisturb)
        }
        nowDisallowed.forEach { event ->
            cancelTimeouts(event.id)
            val result = EventQueue.reduce(stack, QueueCommand.Remove(event.id))
            stack = result.stack
        }
        if (nowDisallowed.isNotEmpty()) {
            phase = IslandStateMachine.next(
                phase,
                if (stack.hasEvents) IslandTrigger.EventRemoved(stack.events.size) else IslandTrigger.QueueCleared,
                transitionContext(),
            )
            armAnimationFallback()
        }

        applySuppression()
        logger.d(TAG, "settings updated")
    }

    private fun onDeviceChanged(next: DeviceContext) {
        val previous = device
        device = next
        if (previous == next) return

        if (previous.screenOn && !next.screenOn) {
            expandedByUser = false
            phase = IslandStateMachine.next(phase, IslandTrigger.ScreenTurnedOff, transitionContext())
        } else if (!previous.screenOn && next.screenOn) {
            phase = IslandStateMachine.next(phase, IslandTrigger.ScreenTurnedOn, transitionContext())
        }
        if (previous.screenLocked != next.screenLocked) {
            stack = stack.copy(events = stack.events.map { PrivacyMasker.mask(it, settings, next.screenLocked) })
        }
        applySuppression()
        logger.d(
            TAG,
            "device: screenOn=${next.screenOn} locked=${next.screenLocked} dnd=${next.doNotDisturb} landscape=${next.landscape}",
        )
    }

    private fun onEnableChanged(enabled: Boolean, reason: DisabledReason?) {
        userEnabled = enabled
        externalDisabledReason = reason
        applySuppression()
        logger.i(TAG, "enabled=$enabled reason=$reason")
    }

    private fun onExpand() {
        if (!stack.hasEvents) return
        expandedByUser = true
        phase = IslandStateMachine.next(phase, IslandTrigger.ExpandRequested, transitionContext())
        armAnimationFallback()
        stack.focused?.let { recordHistory(it, HistoryAction.EXPANDED) }
    }

    private fun onCollapse() {
        expandedByUser = false
        pinnedByUser = false
        phase = IslandStateMachine.next(phase, IslandTrigger.CollapseRequested, transitionContext())
        armAnimationFallback()
        stack.focused?.let { recordHistory(it, HistoryAction.COLLAPSED) }
    }

    private fun onInteractionStart() {
        // Suspend every auto-collapse while the user's finger is on the island.
        timeoutJobs.values.forEach { (collapse, removal) -> collapse?.cancel(); removal?.cancel() }
        animationFallbackJob?.cancel()
        phase = IslandStateMachine.next(phase, IslandTrigger.InteractionStarted, transitionContext())
    }

    private fun onInteractionEnd() {
        phase = IslandStateMachine.next(phase, IslandTrigger.InteractionEnded, transitionContext())
        armAnimationFallback()
        rescheduleTimeouts()
    }

    private fun onAnimationDone() {
        animationFallbackJob?.cancel()
        val before = phase
        phase = IslandStateMachine.next(phase, IslandTrigger.AnimationCompleted, transitionContext())
        if (before != phase && IslandStateMachine.isExpandedPhase(phase)) {
            // Entering a stable expanded phase: start the intro/auto-collapse window.
            stack.focused?.let { focused ->
                val policy = ExpirationPolicy.policyFor(focused, settings)
                scheduleTimeouts(focused, policy, isUpdate = timeoutJobs.containsKey(focused.id))
            }
        }
        applySuppression()
    }

    private fun onCycleFocus(delta: Int) {
        val result = EventQueue.reduce(stack, QueueCommand.Cycle(delta))
        if (!result.focusChanged) return
        stack = result.stack
        expandedByUser = true
        phase = if (phase == IslandPhase.COLLAPSED || phase == IslandPhase.IDLE) {
            IslandStateMachine.next(phase, IslandTrigger.ExpandRequested, transitionContext())
        } else {
            phase
        }
        armAnimationFallback()
    }

    private fun onPin() {
        if (!stack.hasEvents) return
        pinnedByUser = true
        phase = IslandStateMachine.next(phase, IslandTrigger.UserPinned, transitionContext())
    }

    private fun onUnpin() {
        pinnedByUser = false
        phase = IslandStateMachine.next(phase, IslandTrigger.UserUnpinned, transitionContext())
        armAnimationFallback()
    }

    private fun onCollapseTimeout(eventId: String, intro: Boolean) {
        // Only the focused event can collapse the display; background events simply wait.
        if (stack.focusId != eventId) return
        if (phase == IslandPhase.INTERACTING) return
        val trigger = if (intro) IslandTrigger.IntroElapsed else IslandTrigger.AutoCollapseElapsed
        phase = IslandStateMachine.next(phase, trigger, transitionContext())
        armAnimationFallback()
        if (phase == IslandPhase.COLLAPSING || phase == IslandPhase.COLLAPSED) expandedByUser = false
        logger.d(TAG, "timeout collapse id=$eventId intro=$intro phase=$phase")
    }

    private fun onRemovalTimeout(eventId: String) {
        if (phase == IslandPhase.INTERACTING && stack.focusId == eventId) {
            // The user is holding the island: extend the window instead of yanking the event away.
            rescheduleTimeouts()
            return
        }
        onRemoved(eventId, userDismissed = false)
    }

    // endregion

    // region suppression / gating

    /** Why the island must not render right now, or null when it may. */
    private fun computeSuppression(): DisabledReason? {
        if (!userEnabled) return externalDisabledReason ?: DisabledReason.USER_DISABLED
        externalDisabledReason?.let { return it }
        if (!device.screenOn && settings.behavior.hideWhenScreenOff) return DisabledReason.SCREEN_OFF
        if (device.screenLocked && !settings.behavior.showOnLockScreen) return DisabledReason.SCREEN_OFF
        if (device.landscape && settings.behavior.landscapeBehavior == LandscapeBehavior.HIDE) {
            return DisabledReason.LANDSCAPE_HIDDEN
        }
        return null
    }

    private fun applySuppression() {
        val reason = computeSuppression()
        if (reason != null && phase != IslandPhase.DISABLED) {
            expandedByUser = false
            phase = IslandStateMachine.next(phase, IslandTrigger.Disabled(reason), transitionContext())
        } else if (reason == null && phase == IslandPhase.DISABLED) {
            phase = IslandStateMachine.next(phase, IslandTrigger.Enabled, transitionContext())
            armAnimationFallback()
        }
    }

    private fun isDismissalSnoozed(event: IslandEvent): Boolean {
        val at = userDismissals[event.coalesceKey] ?: return false
        val window = if (event.persistent) PERSISTENT_DISMISSAL_SNOOZE_MS else TRANSIENT_DISMISSAL_SNOOZE_MS
        if (clock.elapsedMs() - at < window) return true
        userDismissals.remove(event.coalesceKey)
        return false
    }

    // endregion

    // region timeouts

    private fun scheduleTimeouts(event: IslandEvent, policy: TimeoutPolicy, isUpdate: Boolean) {
        val existing = timeoutJobs[event.id]
        // A refreshed event keeps its original deadlines: chatty sources cannot extend themselves.
        if (isUpdate && existing != null) return
        cancelTimeouts(event.id)

        val intro = policy.removeAfterMs == null
        val collapseJob = policy.collapseAfterMs?.let { ms ->
            scope.launch {
                delay(ms)
                commands.send(Command.CollapseTimeout(event.id, intro))
            }
        }
        val removalJob = policy.removeAfterMs?.let { ms ->
            scope.launch {
                delay(ms)
                commands.send(Command.RemovalTimeout(event.id))
            }
        }
        if (collapseJob != null || removalJob != null) {
            timeoutJobs[event.id] = collapseJob to removalJob
        }
    }

    private fun cancelTimeouts(eventId: String) {
        timeoutJobs.remove(eventId)?.let { (collapse, removal) ->
            collapse?.cancel()
            removal?.cancel()
        }
    }

    private fun rescheduleTimeouts() {
        stack.events.forEach { event ->
            scheduleTimeouts(event, ExpirationPolicy.policyFor(event, settings), isUpdate = false)
        }
    }

    /**
     * Safety net: if the renderer never reports [onAnimationCompleted] (overlay hidden, window
     * removed, OEM quirk) the state machine would otherwise be stuck mid-transition.
     */
    private fun armAnimationFallback() {
        if (phase != IslandPhase.EXPANDING && phase != IslandPhase.COLLAPSING) return
        animationFallbackJob?.cancel()
        animationFallbackJob = scope.launch {
            delay(animationFallbackMs)
            commands.send(Command.AnimationDone)
        }
    }

    // endregion

    private fun transitionContext() = TransitionContext(
        hasEvents = stack.hasEvents,
        hasPersistentEvents = stack.hasPersistentEvents,
        hasTransientEvents = stack.hasTransientEvents,
        expandedByUser = expandedByUser,
        pinnedByUser = pinnedByUser,
    )

    private fun publish() {
        val suppression = computeSuppression()
        val previous = _uiState.value
        val transitionAt = if (previous.phase != phase || previous.focusedEventId != stack.focusId) {
            clock.wallClockMs()
        } else {
            previous.lastTransitionAtMs
        }
        val state = IslandUiState(
            phase = phase,
            events = stack.browsable,
            focusedEventId = stack.focusId,
            focusGeneration = stack.generation,
            expanded = expandedByUser || IslandStateMachine.isExpandedPhase(phase),
            userInteracting = phase == IslandPhase.INTERACTING,
            enabled = userEnabled && suppression == null,
            disabledReason = suppression,
            screenLocked = device.screenLocked,
            screenOff = !device.screenOn,
            doNotDisturb = device.doNotDisturb,
            landscape = device.landscape,
            lastEventAtMs = stack.focused?.createdAt ?: 0L,
            lastTransitionAtMs = transitionAt,
        )
        if (_uiState.value != state) _uiState.value = state
    }

    private fun recordHistory(event: IslandEvent, action: HistoryAction) {
        val repository = historyRepository ?: return
        if (!settings.privacy.historyEnabled) return
        val entry = HistoryEntry(
            id = event.id,
            atMs = clock.wallClockMs(),
            type = event.type,
            priority = event.priority,
            sourcePackage = event.sourcePackage,
            sourceLabel = event.sourceLabel,
            action = action,
            titlePreview = if (settings.privacy.historyStoresContent) Redaction.truncate(event.title) else null,
        )
        scope.launch { runCatching { repository.record(entry) } }
    }

    companion object {
        private const val TAG = "IslandEngine"

        /** Aggregation window for chatty sources (progress notifications, media ticks). */
        const val DEFAULT_COALESCE_WINDOW_MS = 100L

        /** Longest an EXPANDING/COLLAPSING transition may wait for the renderer. */
        const val DEFAULT_ANIMATION_FALLBACK_MS = 700L

        /** A dismissed persistent event (music) may not immediately reappear. */
        const val PERSISTENT_DISMISSAL_SNOOZE_MS = 60_000L

        /** A dismissed transient event may reappear quickly if the source reposts it. */
        const val TRANSIENT_DISMISSAL_SNOOZE_MS = 4_000L
    }
}
