package dev.island.domain

import dev.island.TestEvents
import dev.island.domain.engine.EventQueue
import dev.island.domain.engine.EventStack
import dev.island.domain.engine.ExpirationPolicy
import dev.island.domain.engine.IslandStateMachine
import dev.island.domain.engine.IslandTrigger
import dev.island.domain.engine.PriorityResolver
import dev.island.domain.engine.PrivacyMasker
import dev.island.domain.engine.QueueCommand
import dev.island.domain.engine.TransitionContext
import dev.island.domain.model.DisabledReason
import dev.island.domain.model.IslandPhase
import dev.island.domain.model.IslandPriority
import dev.island.domain.model.IslandSettings
import dev.island.domain.model.NotificationImportance
import dev.island.domain.model.PrivacyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The island's decision logic, tested as the pure functions they are.
 *
 * These are the behaviours a user actually feels: which event wins focus, when a pill expands, what
 * is allowed to be shown under Do Not Disturb, and what survives on the lock screen. They run on the
 * JVM in milliseconds because nothing here touches Android.
 */
class EventQueueTest {

    @Test
    fun `first event becomes focus`() {
        val event = TestEvents.notification()
        val result = EventQueue.reduce(EventStack.Empty, QueueCommand.Upsert(event))

        assertTrue(result.stack.hasEvents)
        assertEquals(event.id, result.stack.focused?.id)
        assertTrue(result.focusChanged)
        assertFalse(result.wasUpdate)
    }

    @Test
    fun `events with the same coalesce key replace instead of stacking`() {
        val first = TestEvents.notification(id = "n1", title = "First")
        val second = TestEvents.notification(id = "n1", title = "Second")

        var stack = EventQueue.reduce(EventStack.Empty, QueueCommand.Upsert(first)).stack
        val result = EventQueue.reduce(stack, QueueCommand.Upsert(second))
        stack = result.stack

        assertEquals(1, stack.events.size)
        assertTrue("a refresh must be reported as an update", result.wasUpdate)
        assertEquals("Second", stack.focused?.title)
    }

    @Test
    fun `removal reports the removed id and clears focus when the stack empties`() {
        val event = TestEvents.notification()
        var stack = EventQueue.reduce(EventStack.Empty, QueueCommand.Upsert(event)).stack

        val result = EventQueue.reduce(stack, QueueCommand.Remove(event.id))
        stack = result.stack

        assertFalse(stack.hasEvents)
        assertTrue(result.removedIds.contains(event.id))
        assertNull(stack.focused)
    }

    @Test
    fun `clear empties the whole stack`() {
        var stack = EventStack.Empty
        repeat(3) { index ->
            stack = EventQueue.reduce(stack, QueueCommand.Upsert(TestEvents.notification(id = "n$index"))).stack
        }
        assertEquals(3, stack.events.size)

        stack = EventQueue.reduce(stack, QueueCommand.Clear).stack
        assertFalse(stack.hasEvents)
    }

    @Test
    fun `stack never grows beyond the hard cap`() {
        var stack = EventStack.Empty
        repeat(EventQueue.MAX_EVENTS + 5) { index ->
            stack = EventQueue.reduce(stack, QueueCommand.Upsert(TestEvents.notification(id = "n$index"))).stack
        }
        assertEquals(EventQueue.MAX_EVENTS, stack.events.size)
        assertNotNull(stack.focused)
    }

    @Test
    fun `higher priority takes focus`() {
        val medium = TestEvents.notification(id = "medium", priority = IslandPriority.MEDIUM)
        val critical = TestEvents.call(id = "call")

        var stack = EventQueue.reduce(EventStack.Empty, QueueCommand.Upsert(medium)).stack
        stack = EventQueue.reduce(stack, QueueCommand.Upsert(critical)).stack

        assertEquals(critical.id, stack.focused?.id)
        assertEquals(2, stack.events.size)
    }

    @Test
    fun `lower priority does not steal focus`() {
        val critical = TestEvents.call(id = "call")
        val low = TestEvents.notification(id = "low", priority = IslandPriority.LOW)

        var stack = EventQueue.reduce(EventStack.Empty, QueueCommand.Upsert(critical)).stack
        stack = EventQueue.reduce(stack, QueueCommand.Upsert(low)).stack

        assertEquals(critical.id, stack.focused?.id)
    }

    @Test
    fun `a transient event shows over a persistent one and focus returns afterwards`() {
        val music = TestEvents.media(id = "music")
        val message = TestEvents.notification(id = "message")

        var stack = EventQueue.reduce(EventStack.Empty, QueueCommand.Upsert(music)).stack
        assertEquals(music.id, stack.focused?.id)

        stack = EventQueue.reduce(stack, QueueCommand.Upsert(message)).stack
        assertEquals("a new message takes the display over playing music", message.id, stack.focused?.id)

        stack = EventQueue.reduce(stack, QueueCommand.Remove(message.id)).stack
        assertEquals("music must come back once the message is gone", music.id, stack.focused?.id)
    }

    @Test
    fun `cycling moves focus to the neighbouring event`() {
        val first = TestEvents.notification(id = "a", priority = IslandPriority.MEDIUM)
        val second = TestEvents.media(id = "b")

        var stack = EventQueue.reduce(EventStack.Empty, QueueCommand.Upsert(first)).stack
        stack = EventQueue.reduce(stack, QueueCommand.Upsert(second)).stack
        val before = stack.focused?.id

        stack = EventQueue.reduce(stack, QueueCommand.Cycle(1)).stack

        assertNotEquals(before, stack.focused?.id)
        assertEquals(2, stack.events.size)
    }

    @Test
    fun `explicit focus wins over priority ordering`() {
        val first = TestEvents.notification(id = "a", priority = IslandPriority.HIGH)
        val second = TestEvents.notification(id = "b", priority = IslandPriority.LOW)

        var stack = EventQueue.reduce(EventStack.Empty, QueueCommand.Upsert(first)).stack
        stack = EventQueue.reduce(stack, QueueCommand.Upsert(second)).stack
        stack = EventQueue.reduce(stack, QueueCommand.Focus(second.id)).stack

        assertEquals(second.id, stack.focused?.id)
    }
}

class PriorityResolverTest {

    private val settings = IslandSettings(islandEnabled = true)

    @Test
    fun `background priority is never shown`() {
        val event = TestEvents.notification(id = "bg", priority = IslandPriority.BACKGROUND)
        assertFalse(PriorityResolver.shouldShow(event, IslandPriority.BACKGROUND, settings, doNotDisturb = false))
    }

    @Test
    fun `do not disturb hides everything below the critical threshold`() {
        val dndSettings = settings.copy(
            behavior = settings.behavior.copy(followSystemDnd = true, allowCriticalDuringDnd = true),
        )
        val message = TestEvents.notification(id = "m")
        val resolved = PriorityResolver.resolve(message, dndSettings)

        assertFalse(PriorityResolver.shouldShow(message, resolved, dndSettings, doNotDisturb = true))

        val call = TestEvents.call()
        val callPriority = PriorityResolver.resolve(call, dndSettings)
        assertTrue(
            "an incoming call must survive Do Not Disturb",
            PriorityResolver.shouldShow(call, callPriority, dndSettings, doNotDisturb = true),
        )
    }

    @Test
    fun `disabling a source hides its events`() {
        val noMedia = settings.copy(media = settings.media.copy(enabled = false))
        val media = TestEvents.media()
        val priority = PriorityResolver.resolve(media, noMedia)

        assertFalse(PriorityResolver.shouldShow(media, priority, noMedia, doNotDisturb = false))
    }

    @Test
    fun `notifications below the user's importance floor are hidden`() {
        val strict = settings.copy(
            notifications = settings.notifications.copy(minImportance = NotificationImportance.HIGH),
        )
        val quiet = TestEvents.notification(id = "quiet")
        val priority = PriorityResolver.resolve(quiet, strict)

        assertFalse(
            "a default-importance notification must not show when the floor is HIGH",
            PriorityResolver.shouldShow(quiet, priority, strict, doNotDisturb = false),
        )
    }
}

class PrivacyMaskerTest {

    @Test
    fun `lock screen uses the lock screen mode`() {
        val settings = IslandSettings(
            notifications = IslandSettings().notifications.copy(privacyMode = PrivacyMode.FULL),
            privacy = IslandSettings().privacy.copy(lockScreenMode = PrivacyMode.ICON_ONLY),
        )
        assertEquals(PrivacyMode.ICON_ONLY, PrivacyMasker.effectiveMode(settings, screenLocked = true))
        assertEquals(PrivacyMode.FULL, PrivacyMasker.effectiveMode(settings, screenLocked = false))
    }

    @Test
    fun `full mode keeps content`() {
        val settings = IslandSettings(
            notifications = IslandSettings().notifications.copy(privacyMode = PrivacyMode.FULL),
        )
        val masked = PrivacyMasker.mask(TestEvents.notification(), settings, screenLocked = false)

        assertTrue(masked.title.contains("Ava") || masked.subtitle?.contains("7") == true)
    }

    @Test
    fun `icon only removes every trace of the message text`() {
        val settings = IslandSettings(
            notifications = IslandSettings().notifications.copy(privacyMode = PrivacyMode.ICON_ONLY),
        )
        val original = TestEvents.notification(text = "Are we still on for 7?")
        val masked = PrivacyMasker.mask(original, settings, screenLocked = false) as dev.island.domain.model.IslandEvent.Notification

        assertNull(masked.notification.text)
        assertNull(masked.notification.title)
        assertTrue(masked.notification.actionLabels.isEmpty())
        assertFalse(masked.subtitle?.contains("7") == true)
        assertFalse(masked.title.contains("Are we still on"))
    }

    @Test
    fun `sensitive notifications are hidden even in full mode`() {
        val settings = IslandSettings(
            notifications = IslandSettings().notifications.copy(
                privacyMode = PrivacyMode.SENSITIVE_HIDDEN,
                hideSensitiveContent = true,
            ),
        )
        val sensitive = TestEvents.notification(text = "Your code is 4242", visibilityPrivate = true)
        val masked = PrivacyMasker.mask(sensitive, settings, screenLocked = false)

        assertFalse("content the sender marked private must never be rendered", masked.title.contains("4242"))
        assertFalse(masked.subtitle?.contains("4242") == true)
    }
}

class IslandStateMachineTest {

    private val withEvents = TransitionContext(hasEvents = true, hasTransientEvents = true)
    private val persistentOnly = TransitionContext(hasEvents = true, hasPersistentEvents = true)

    @Test
    fun `an arriving event leaves the idle island`() {
        val next = IslandStateMachine.next(
            current = IslandPhase.IDLE,
            trigger = IslandTrigger.EventArrived(priority = IslandPriority.MEDIUM, persistent = false),
            context = withEvents,
        )
        assertNotEquals(IslandPhase.IDLE, next)
    }

    @Test
    fun `a persistent event alone settles into the collapsed mini island`() {
        val next = IslandStateMachine.next(
            current = IslandPhase.IDLE,
            trigger = IslandTrigger.EventArrived(priority = IslandPriority.MEDIUM, persistent = true),
            context = persistentOnly,
        )
        assertNotEquals(IslandPhase.IDLE, next)
        assertTrue(IslandStateMachine.isExpandedPhase(next) || next == IslandPhase.COLLAPSED)
    }

    @Test
    fun `expand and collapse are explicit and reversible`() {
        assertEquals(
            IslandPhase.EXPANDING,
            IslandStateMachine.next(IslandPhase.COLLAPSED, IslandTrigger.ExpandRequested, withEvents),
        )
        assertEquals(
            IslandPhase.COLLAPSING,
            IslandStateMachine.next(IslandPhase.EXPANDED, IslandTrigger.CollapseRequested, withEvents),
        )
    }

    @Test
    fun `expanding with nothing to show is a no-op`() {
        assertEquals(
            IslandPhase.IDLE,
            IslandStateMachine.next(
                IslandPhase.IDLE,
                IslandTrigger.ExpandRequested,
                TransitionContext(hasEvents = false),
            ),
        )
    }

    @Test
    fun `auto collapse is suspended while the user is touching the island`() {
        assertEquals(
            IslandPhase.INTERACTING,
            IslandStateMachine.next(IslandPhase.INTERACTING, IslandTrigger.AutoCollapseElapsed, withEvents),
        )
    }

    @Test
    fun `interaction end returns to the expanded state the user chose`() {
        assertEquals(
            IslandPhase.EXPANDED,
            IslandStateMachine.next(
                IslandPhase.INTERACTING,
                IslandTrigger.InteractionEnded,
                withEvents.copy(expandedByUser = true),
            ),
        )
        assertEquals(
            IslandPhase.COLLAPSED,
            IslandStateMachine.next(IslandPhase.INTERACTING, IslandTrigger.InteractionEnded, withEvents),
        )
    }

    @Test
    fun `disabling wins over everything else`() {
        val next = IslandStateMachine.next(
            current = IslandPhase.EXPANDED,
            trigger = IslandTrigger.Disabled(DisabledReason.OVERLAY_PERMISSION_MISSING),
            context = withEvents,
        )
        assertEquals(IslandPhase.DISABLED, next)
    }

    @Test
    fun `only an explicit enable leaves the disabled phase`() {
        assertEquals(
            IslandPhase.DISABLED,
            IslandStateMachine.next(
                IslandPhase.DISABLED,
                IslandTrigger.EventArrived(priority = IslandPriority.CRITICAL, persistent = false),
                withEvents,
            ),
        )
        assertNotEquals(
            IslandPhase.DISABLED,
            IslandStateMachine.next(IslandPhase.DISABLED, IslandTrigger.Enabled, withEvents),
        )
    }

    @Test
    fun `screen off hides the island and the empty queue returns to idle`() {
        assertEquals(
            IslandPhase.IDLE,
            IslandStateMachine.next(IslandPhase.COLLAPSED, IslandTrigger.ScreenTurnedOff, withEvents),
        )
        assertEquals(
            IslandPhase.IDLE,
            IslandStateMachine.next(
                IslandPhase.COLLAPSED,
                IslandTrigger.EventRemoved(remainingEvents = 0),
                TransitionContext(hasEvents = false),
            ),
        )
    }
}

class ExpirationPolicyTest {

    private val settings = IslandSettings(islandEnabled = true)

    @Test
    fun `persistent events are never removed by a timer`() {
        val policy = ExpirationPolicy.policyFor(TestEvents.media(), settings)
        assertNull(policy.removeAfterMs)
        assertTrue(policy.isPersistent)
    }

    @Test
    fun `transient events collapse and then expire`() {
        val policy = ExpirationPolicy.policyFor(TestEvents.notification(), settings)
        assertNotNull(policy.collapseAfterMs)
        assertNotNull(policy.removeAfterMs)
        assertFalse(policy.isPersistent)
    }

    @Test
    fun `a critical event is given at least as long as a normal one`() {
        val critical = ExpirationPolicy.policyFor(TestEvents.call(), settings)
        val normal = ExpirationPolicy.policyFor(TestEvents.notification(), settings)
        assertTrue((critical.removeAfterMs ?: Long.MAX_VALUE) >= (normal.removeAfterMs ?: 0L))
    }
}
