package com.mikai233.common.test.db.tracked

import com.mikai233.common.db.tracked.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PendingWriteQueueTest {
    private val rootPath = DbPath("player", 1, "1001", "data.bag")

    @Test
    fun `property delegate records last field value`() {
        val queue = PendingWriteQueue()
        val entity = ScalarTrackedEntity(TrackContext("scalar", 1, "1001", queue, "data"), "old", 1)

        entity.name = "new"
        entity.level += 2
        entity.level += 3

        val write = queue.snapshot().single()
        assertEquals(mapOf("data.name" to "new", "data.level" to 6), write.sets)
        assertTrue(write.unsets.isEmpty())
    }

    @Test
    fun `map put remove and put again are merged before drain`() {
        val queue = PendingWriteQueue()
        val map = TrackedMutableMap(rootPath, linkedMapOf<Int, String>(), queue)

        map[1] = "a"
        map.remove(1)
        map[1] = "b"

        val write = queue.drain().single()
        assertEquals(mapOf("data.bag.1" to "b"), write.sets)
        assertTrue(write.unsets.isEmpty())
        assertTrue(queue.snapshot().isEmpty())
    }

    @Test
    fun `map entry setValue is tracked`() {
        val queue = PendingWriteQueue()
        val map = TrackedMutableMap(rootPath, linkedMapOf(1 to "a"), queue)

        map.entries.single().setValue("b")

        val write = queue.snapshot().single()
        assertEquals(mapOf("data.bag.1" to "b"), write.sets)
    }

    @Test
    fun `equal map and list assignments are not enqueued`() {
        val queue = PendingWriteQueue()
        val map = TrackedMutableMap(rootPath, linkedMapOf(1 to "a"), queue)
        val list = TrackedMutableList(DbPath("player", 1, "1001", "data.tags"), mutableListOf("pve"), queue)

        map[1] = "a"
        map.entries.single().setValue("a")
        list[0] = "pve"

        assertTrue(queue.snapshot().isEmpty())
    }

    @Test
    fun `map iterator remove is tracked`() {
        val queue = PendingWriteQueue()
        val map = TrackedMutableMap(rootPath, linkedMapOf(1 to "a"), queue)
        val iterator = map.entries.iterator()

        iterator.next()
        iterator.remove()

        val write = queue.snapshot().single()
        assertEquals(setOf("data.bag.1"), write.unsets)
    }

    @Test
    fun `map keys remove is tracked`() {
        val queue = PendingWriteQueue()
        val map = TrackedMutableMap(rootPath, linkedMapOf(1 to "a"), queue)

        map.keys.remove(1)

        val write = queue.snapshot().single()
        assertEquals(setOf("data.bag.1"), write.unsets)
    }

    @Test
    fun `map values remove is tracked`() {
        val queue = PendingWriteQueue()
        val map = TrackedMutableMap(rootPath, linkedMapOf(1 to "a"), queue)

        map.values.remove("a")

        val write = queue.snapshot().single()
        assertEquals(setOf("data.bag.1"), write.unsets)
    }

    @Test
    fun `parent path operation removes child operations`() {
        val queue = PendingWriteQueue()
        queue.enqueue(ChangeOp.Set(rootPath.child(1), "a"))
        queue.enqueue(ChangeOp.Set(rootPath, emptyMap<Int, String>()))

        val write = queue.snapshot().single()
        assertEquals(mapOf("data.bag" to emptyMap<Int, String>()), write.sets)
        assertTrue(write.unsets.isEmpty())
    }

    private class ScalarTrackedEntity(
        ctx: TrackContext,
        name: String,
        level: Int,
    ) {
        var name: String by trackedValue(ctx.path("name"), name, ctx.queue)
        var level: Int by trackedValue(ctx.path("level"), level, ctx.queue)
    }
}
