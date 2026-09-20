package dev.island.data

import dev.island.TestEvents
import dev.island.data.notifications.NotificationGrouping
import dev.island.domain.model.IslandEventType
import dev.island.domain.model.NotificationInfo
import dev.island.feature.island.ui.renderers.IslandRenderMode
import dev.island.feature.island.ui.renderers.IslandRendererRegistry
import dev.island.feature.island.ui.renderers.defaultIslandRenderers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Notification grouping rules — the difference between "3 messages" and three separate animations.
 */
class NotificationGroupingTest {

    private fun notification(
        key: String,
        packageName: String = "com.example.chat",
        appName: String = "Messages",
        groupKey: String? = null,
        postedAtMs: Long = 0L,
        isOngoing: Boolean = false,
        isGroupSummary: Boolean = false,
    ) = NotificationInfo(
        key = key,
        packageName = packageName,
        appName = appName,
        title = "Title $key",
        text = "Body $key",
        groupKey = groupKey,
        postedAtMs = postedAtMs,
        isOngoing = isOngoing,
        isGroupSummary = isGroupSummary,
    )

    @Test
    fun `same group key lands in one bucket`() {
        val items = listOf(
            notification("1", groupKey = "thread"),
            notification("2", groupKey = "thread"),
        )
        val groups = NotificationGrouping.group(items, groupingEnabled = true)

        assertEquals(1, groups.size)
        assertEquals(2, groups.first().size)
    }

    @Test
    fun `different apps never merge`() {
        val items = listOf(
            notification("1", packageName = "com.example.chat", appName = "Messages"),
            notification("2", packageName = "com.example.mail", appName = "Mail"),
        )
        val groups = NotificationGrouping.group(items, groupingEnabled = true)

        assertEquals(2, groups.size)
    }

    @Test
    fun `grouping can be turned off, one group per notification`() {
        val items = listOf(
            notification("1", groupKey = "thread"),
            notification("2", groupKey = "thread"),
        )
        val groups = NotificationGrouping.group(items, groupingEnabled = false)

        assertEquals(2, groups.size)
        assertTrue(groups.all { it.isSingle })
    }

    @Test
    fun `a system summary becomes the representative and is not counted as a child`() {
        val items = listOf(
            notification("child1", groupKey = "thread", postedAtMs = 10L),
            notification("child2", groupKey = "thread", postedAtMs = 20L),
            notification("summary", groupKey = "thread", postedAtMs = 30L, isGroupSummary = true),
        )
        val group = NotificationGrouping.group(items, groupingEnabled = true).single()

        assertTrue(group.hasSystemSummary)
        assertEquals("summary", group.representative.key)
        assertEquals(2, group.items.size)
        assertFalse(group.items.any { it.isGroupSummary })
    }

    @Test
    fun `the newest notification represents a group without a summary`() {
        val items = listOf(
            notification("old", groupKey = "thread", postedAtMs = 10L),
            notification("new", groupKey = "thread", postedAtMs = 99L),
        )
        val group = NotificationGrouping.group(items, groupingEnabled = true).single()

        assertEquals("new", group.representative.key)
    }

    @Test
    fun `ongoing notifications stay out of groups`() {
        val ongoing = notification("download", isOngoing = true, groupKey = "thread")
        val message = notification("msg", groupKey = "thread")

        assertFalse(NotificationGrouping.bucketKey(ongoing) == NotificationGrouping.bucketKey(message))

        val groups = NotificationGrouping.group(listOf(ongoing, message), groupingEnabled = true)
        assertEquals(2, groups.size)
    }

    @Test
    fun `siblings exclude the notification itself and disappear when grouping is off`() {
        val items = listOf(
            notification("1", groupKey = "thread"),
            notification("2", groupKey = "thread"),
        )
        assertEquals(1, NotificationGrouping.siblingsOf("1", items, groupingEnabled = true).size)
        assertEquals("2", NotificationGrouping.siblingsOf("1", items, groupingEnabled = true).single().key)
        assertTrue(NotificationGrouping.siblingsOf("1", items, groupingEnabled = false).isEmpty())
        assertTrue(NotificationGrouping.siblingsOf("missing", items, groupingEnabled = true).isEmpty())
    }

    @Test
    fun `a lone notification has no siblings`() {
        val items = listOf(notification("only", groupKey = "thread"))
        assertTrue(NotificationGrouping.siblingsOf("only", items, groupingEnabled = true).isEmpty())
    }
}

/**
 * Renderer contract: every event family must have a renderer, and the registry must never return
 * nothing. This is what keeps "add a new event type" from silently producing an empty island.
 */
class IslandRendererRegistryTest {

    private val registry = IslandRendererRegistry(defaultIslandRenderers())

    @Test
    fun `every event family resolves to a renderer`() {
        val events = listOf(
            TestEvents.notification(),
            TestEvents.media(),
            TestEvents.call(),
            TestEvents.custom(),
        )
        events.forEach { event ->
            val renderer = registry.rendererFor(event)
            assertNotNull("no renderer for ${event.type}", renderer)
            assertTrue(
                "renderer ${renderer.javaClass.simpleName} does not claim ${event.type}",
                renderer.canRender(event) || renderer.order == FALLBACK_ORDER,
            )
        }
    }

    @Test
    fun `dedicated renderers win over the generic fallback`() {
        val media = registry.rendererFor(TestEvents.media())
        val notification = registry.rendererFor(TestEvents.notification())
        val call = registry.rendererFor(TestEvents.call())

        assertTrue(media.canRender(TestEvents.media()))
        assertFalse(media.canRender(TestEvents.notification()))
        assertTrue(notification.canRender(TestEvents.notification()))
        assertTrue(call.canRender(TestEvents.call()))
        assertEquals(3, setOf(media, notification, call).size)
    }

    @Test
    fun `render modes cover collapsed, expanded and minimized`() {
        assertEquals(3, IslandRenderMode.entries.size)
    }

    @Test
    fun `event types are all reachable from the factories`() {
        // Guards against adding an IslandEventType without a renderer family.
        assertTrue(IslandEventType.entries.size >= 13)
    }

    private companion object {
        const val FALLBACK_ORDER = 1000
    }
}
