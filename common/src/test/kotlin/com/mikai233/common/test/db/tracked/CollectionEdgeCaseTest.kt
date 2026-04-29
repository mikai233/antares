package com.mikai233.common.test.db.tracked

import com.mikai233.common.db.tracked.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class CollectionEdgeCaseTest {
    @Test
    fun `nullable map value set null is different from remove`() {
        val queue = PendingWriteQueue()
        val map = TrackedMutableMap(DbPath("slot", 1, "entity", "data.options"), linkedMapOf<Int, String?>(), queue)

        map[1] = null
        map.remove(1)

        val write = queue.snapshot().single()
        assertTrue(write.sets.isEmpty())
        assertEquals(setOf("data.options.1"), write.unsets)
    }

    @Test
    fun `tree map keeps sorted backing semantics`() {
        val queue = PendingWriteQueue()
        val map = TrackedMutableMap(DbPath("slot", 1, "entity", "data.rank"), TreeMap<Int, String>(), queue)

        map[3] = "c"
        map[1] = "a"
        map[2] = "b"

        assertIterableEquals(listOf(1, 2, 3), map.keys)
        val persistentMap = map.toPersistentValue() as Map<*, *>
        assertIterableEquals(listOf("1", "2", "3"), persistentMap.keys)
    }

    @Test
    fun `tree set keeps sorted backing semantics`() {
        val queue = PendingWriteQueue()
        val set = TrackedMutableSet(DbPath("slot", 1, "entity", "data.sorted"), TreeSet<Int>(), queue)

        set.add(3)
        set.add(1)
        set.add(2)

        assertIterableEquals(listOf(1, 2, 3), set)
        assertEquals(listOf(1, 2, 3), set.toPersistentValue())
    }

    @Test
    fun `set with nested mutable containers updates the whole set`() {
        val queue = PendingWriteQueue()
        val groups: MutableSet<MutableList<MutableMap<String, Int>>> =
            linkedSetOf(mutableListOf(linkedMapOf("score" to 1)))
        val set = TrackedMutableSet(
            DbPath("slot", 1, "entity", "data.groups"),
            groups,
            queue,
        )

        set.first()[0]["score"] = 2

        val write = queue.snapshot().single()
        assertEquals(listOf(listOf(mapOf("score" to 2))), write.sets["data.groups"])
        assertTrue(write.unsets.isEmpty())
    }

    @Test
    fun `set with tracked object element updates the whole set when object changes`() {
        val queue = PendingWriteQueue()
        val set = TrackedMutableSet(
            DbPath("slot", 1, "entity", "data.items"),
            linkedSetOf(SetItemTracked(DbPath("slot", 1, "entity", "data.items"), queue, 1, 10)),
            queue,
        )

        set.first().count = 11

        val write = queue.snapshot().single()
        assertEquals(listOf(mapOf("id" to 1, "count" to 11)), write.sets["data.items"])
    }

    @Test
    fun `enum map and enum set use enum names for paths and values`() {
        val queue = PendingWriteQueue()
        val map = TrackedMutableMap(
            DbPath("slot", 1, "entity", "data.resources"),
            EnumMap<Resource, Int>(Resource::class.java),
            queue,
        )
        val set = TrackedMutableSet(
            DbPath("slot", 1, "entity", "data.flags"),
            EnumSet.noneOf(Resource::class.java),
            queue,
        )

        map[Resource.Gold] = 100
        set.add(Resource.Gem)

        val write = queue.snapshot().single()
        assertEquals(100, write.sets["data.resources.Gold"])
        assertEquals(listOf("Gem"), write.sets["data.flags"])
    }

    @Test
    fun `persistent values use encoded mongo field keys and list collections`() {
        val value = linkedMapOf<Any?, Any?>(
            "a.b" to 1,
            "cost\$gold" to linkedMapOf(2 to "silver"),
            Resource.Gold to linkedSetOf(Resource.Gem),
        )

        assertEquals(
            linkedMapOf(
                "a%2Eb" to 1,
                "cost%24gold" to linkedMapOf("2" to "silver"),
                "Gold" to listOf("Gem"),
            ),
            persistentValueOf(value),
        )
    }

    @Test
    fun `nested map iterator remove is lifted to dirty target boundary`() {
        val queue = PendingWriteQueue()
        val initialValue: MutableList<MutableMap<String, Int>> = mutableListOf(linkedMapOf("a" to 1, "b" to 2))
        val list = TrackedMutableList(
            DbPath("slot", 1, "entity", "data.items"),
            initialValue,
            queue,
        )
        val nested = list[0]
        val iterator = nested.entries.iterator()

        while (iterator.hasNext()) {
            if (iterator.next().key == "a") {
                iterator.remove()
            }
        }

        val write = queue.snapshot().single()
        assertEquals(mapOf("b" to 2), write.sets["data.items.0"])
        assertTrue(write.unsets.isEmpty())
    }

    @Test
    fun `deque operations keep familiar queue api and persist final order`() {
        val queue = PendingWriteQueue()
        val entity = DequeTrackedEntity(TrackContext("slot", 1, "entity", queue, "data"), ArrayDeque(listOf(2, 3)))

        entity.pending.addFirst(1)
        entity.pending.addLast(4)
        entity.pending.removeFirst()
        entity.pending.pollLast()

        val write = queue.snapshot().single()
        assertEquals(listOf(2, 3), write.sets["data.pending"])
    }

    @Test
    fun `tracked primitive arrays support index assignment`() {
        val queue = PendingWriteQueue()
        val entity = ArrayTrackedEntity(
            ctx = TrackContext("slot", 1, "entity", queue, "data"),
            scores = intArrayOf(1, 2, 3),
            totals = longArrayOf(10L, 20L),
            flags = booleanArrayOf(false, false),
            ratios = doubleArrayOf(1.0, 2.0),
            weights = floatArrayOf(1.5F, 2.5F),
        )

        entity.scores[1] = 9
        entity.totals[0] = 99L
        entity.flags[1] = true
        entity.ratios[0] = 3.5
        entity.weights[1] = 4.5F

        val write = queue.snapshot().single()
        assertEquals(9, write.sets["data.scores.1"])
        assertEquals(99L, write.sets["data.totals.0"])
        assertEquals(true, write.sets["data.flags.1"])
        assertEquals(3.5, write.sets["data.ratios.0"])
        assertEquals(4.5F, write.sets["data.weights.1"])
        assertEquals(listOf(1, 9, 3), entity.scores.toPersistentValue())
        assertEquals(listOf(99L, 20L), entity.totals.toPersistentValue())
        assertEquals(listOf(false, true), entity.flags.toPersistentValue())
        assertEquals(listOf(3.5, 2.0), entity.ratios.toPersistentValue())
        assertEquals(listOf(1.5F, 4.5F), entity.weights.toPersistentValue())
    }

    private class DequeTrackedEntity(
        ctx: TrackContext,
        pending: ArrayDeque<Int>,
    ) {
        val pending: java.util.Deque<Int> by trackedDeque(ctx.path("pending"), pending, ctx.queue)
    }

    private class ArrayTrackedEntity(
        ctx: TrackContext,
        scores: IntArray,
        totals: LongArray,
        flags: BooleanArray,
        ratios: DoubleArray,
        weights: FloatArray,
    ) {
        val scores by trackedIntArray(ctx.path("scores"), scores, ctx.queue)
        val totals by trackedLongArray(ctx.path("totals"), totals, ctx.queue)
        val flags by trackedBooleanArray(ctx.path("flags"), flags, ctx.queue)
        val ratios by trackedDoubleArray(ctx.path("ratios"), ratios, ctx.queue)
        val weights by trackedFloatArray(ctx.path("weights"), weights, ctx.queue)
    }

    private enum class Resource {
        Gold,
        Gem,
    }

    private class SetItemTracked(
        private val path: DbPath,
        queue: PendingWriteQueue,
        val id: Int,
        count: Int,
    ) : TrackedObjectSupport(queue) {
        var count: Int = count
            set(value) {
                if (field == value) {
                    return
                }
                field = value
                markSet(path.child("count"), value)
            }

        override fun toPersistentValue(): Any? {
            return mapOf(
                "id" to id,
                "count" to count,
            )
        }

        // Set elements must use immutable identity only; mutable tracked fields must not participate.
        override fun equals(other: Any?): Boolean {
            return other is SetItemTracked && other.id == id
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }
}
