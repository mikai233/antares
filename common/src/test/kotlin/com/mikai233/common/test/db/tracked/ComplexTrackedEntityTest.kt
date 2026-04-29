package com.mikai233.common.test.db.tracked

import com.mikai233.common.db.tracked.DbPath
import com.mikai233.common.db.tracked.ChangeQueue
import com.mikai233.common.db.tracked.DirtyTarget
import com.mikai233.common.db.tracked.DirtyTargetAware
import com.mikai233.common.db.tracked.PendingWriteQueue
import com.mikai233.common.db.tracked.PersistentValue
import com.mikai233.common.db.tracked.TrackContext
import com.mikai233.common.db.tracked.TrackedObjectSupport
import com.mikai233.common.db.tracked.persistentValueOf
import com.mikai233.common.db.tracked.trackedList
import com.mikai233.common.db.tracked.trackedMap
import com.mikai233.common.db.tracked.trackedSet
import com.mikai233.common.db.tracked.trackedValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComplexTrackedEntityTest {
    @Test
    fun `complex wrapper tracks common collection operations and nested collection mutations`() {
        val queue = PendingWriteQueue()
        val entity = ComplexTrackedEntity.create(TrackContext("complex_slot", 3, "entity:1", queue))

        entity.title = "season-two"
        entity.stats["attack"] = 12
        entity.tags[1] = "pvp"
        entity.unlocked.add("tower")
        entity.scoreBuckets.getValue("daily").add(30)
        entity.scoreBuckets["weekly"] = mutableListOf(1)
        entity.scoreBuckets.getValue("weekly").add(2)
        entity.stageRewards[0]["gold"] = 200
        entity.stageRewards.add(mutableMapOf("star" to 1))
        entity.stageRewards.last()["star"] = 2
        entity.bag.getValue(1001).count += 2
        entity.bag.getValue(1001).attrs["quality"] = "rare"

        val write = queue.snapshot().single()
        assertEquals("complex_slot", write.key.slot)
        assertEquals(3, write.key.bucket)
        assertEquals("entity:1", write.key.entityId)
        assertEquals("season-two", write.sets["data.title"])
        assertEquals(12, write.sets["data.stats.attack"])
        assertEquals("pvp", write.sets["data.tags.1"])
        assertEquals(setOf("arena", "tower"), write.sets["data.unlocked"])
        assertEquals(listOf(10, 20, 30), write.sets["data.scoreBuckets.daily"])
        assertEquals(listOf(1, 2), write.sets["data.scoreBuckets.weekly"])
        assertEquals(
            listOf(mapOf("gold" to 200), mapOf("star" to 2)),
            write.sets["data.stageRewards"],
        )
        assertEquals(
            mapOf("id" to 1001, "count" to 7, "attrs" to mapOf("quality" to "rare")),
            write.sets["data.bag.1001"],
        )
        assertTrue(write.unsets.isEmpty())
    }

    @Test
    fun `immutable val object is kept without wrapper`() {
        val queue = PendingWriteQueue()
        val profile = ImmutableProfile(level = 1, title = "rookie")
        val entity = ImmutableFieldTrackedEntity(TrackContext("complex_slot", 3, "entity:1", queue), profile)

        assertTrue(entity.profile === profile)
        assertTrue(queue.snapshot().isEmpty())
        assertEquals(mapOf("level" to 1, "title" to "rookie"), entity.toPersistentValue())
    }

    @Test
    fun `nested map inside list updates the two-level boundary when parent list is not structurally changed`() {
        val queue = PendingWriteQueue()
        val entity = ComplexTrackedEntity.create(TrackContext("complex_slot", 3, "entity:1", queue))

        entity.stageRewards[0]["gold"] = 200

        val write = queue.snapshot().single()
        assertEquals(mapOf("gold" to 200), write.sets["data.stageRewards.0"])
    }

    @Test
    fun `nested collection added then changed keeps the latest value in parent set operation`() {
        val queue = PendingWriteQueue()
        val entity = ComplexTrackedEntity.create(TrackContext("complex_slot", 3, "entity:1", queue))

        entity.stageRewards.add(mutableMapOf("star" to 1))
        entity.stageRewards.last()["star"] = 3
        entity.stageRewards.last()["gem"] = 9

        val write = queue.snapshot().single()
        assertEquals(
            listOf(mapOf("gold" to 100), mapOf("star" to 3, "gem" to 9)),
            write.sets["data.stageRewards"],
        )
    }

    @Test
    fun `recursive container wrapping tracks more than two nested levels`() {
        val queue = PendingWriteQueue()
        val entity = DeepContainerTrackedEntity(
            TrackContext("complex_slot", 3, "entity:1", queue),
            linkedMapOf(
                "rooms" to mutableListOf(
                    linkedMapOf(7 to mutableListOf("mob")),
                ),
            ),
        )

        entity.deep.getValue("rooms")[0].getValue(7).add("boss")
        entity.deep["events"] = mutableListOf(linkedMapOf(3 to mutableListOf("open")))
        entity.deep.getValue("events")[0].getValue(3).add("close")

        val write = queue.snapshot().single()
        assertEquals(listOf(mapOf(7 to listOf("mob", "boss"))), write.sets["data.deep.rooms"])
        assertEquals(
            listOf(mapOf(3 to listOf("open", "close"))),
            write.sets["data.deep.events"],
        )
    }

    private class ComplexTrackedEntity(
        private val ctx: TrackContext,
        title: String,
        stats: MutableMap<String, Int>,
        tags: MutableList<String>,
        unlocked: MutableSet<String>,
        scoreBuckets: MutableMap<String, MutableList<Int>>,
        stageRewards: MutableList<MutableMap<String, Int>>,
        bag: MutableMap<Int, ItemTracked>,
    ) : PersistentValue {
        var title: String by trackedValue(ctx.path("title"), title, ctx.queue)
        val stats: MutableMap<String, Int> by trackedMap(ctx.path("stats"), stats, ctx.queue)
        val tags: MutableList<String> by trackedList(ctx.path("tags"), tags, ctx.queue)
        val unlocked: MutableSet<String> by trackedSet(ctx.path("unlocked"), unlocked, ctx.queue)
        val scoreBuckets: MutableMap<String, MutableList<Int>> by trackedMap(
            path = ctx.path("scoreBuckets"),
            initialValue = scoreBuckets,
            queue = ctx.queue,
        )
        val stageRewards: MutableList<MutableMap<String, Int>> by trackedList(
            path = ctx.path("stageRewards"),
            initialValue = stageRewards,
            queue = ctx.queue,
        )
        val bag: MutableMap<Int, ItemTracked> by trackedMap(ctx.path("bag"), bag, ctx.queue)

        override fun toPersistentValue(): Any? {
            return mapOf(
                "title" to title,
                "stats" to persistentValueOf(stats),
                "tags" to persistentValueOf(tags),
                "unlocked" to persistentValueOf(unlocked),
                "scoreBuckets" to persistentValueOf(scoreBuckets),
                "stageRewards" to persistentValueOf(stageRewards),
                "bag" to persistentValueOf(bag),
            )
        }

        companion object {
            fun create(ctx: TrackContext): ComplexTrackedEntity {
                return ComplexTrackedEntity(
                    ctx = ctx,
                    title = "season-one",
                    stats = linkedMapOf("attack" to 10),
                    tags = mutableListOf("ranked", "casual"),
                    unlocked = linkedSetOf("arena"),
                    scoreBuckets = linkedMapOf("daily" to mutableListOf(10, 20)),
                    stageRewards = mutableListOf(linkedMapOf("gold" to 100)),
                    bag = linkedMapOf(
                        1001 to ItemTracked(
                            path = ctx.path("bag").child(1001),
                            queue = ctx.queue,
                            id = 1001,
                            count = 5,
                            attrs = linkedMapOf("quality" to "normal"),
                        ),
                    ),
                )
            }
        }
    }

    private class ItemTracked(
        private val path: DbPath,
        queue: ChangeQueue,
        val id: Int,
        count: Int,
        attrs: MutableMap<String, String>,
    ) : TrackedObjectSupport(queue) {
        var count: Int = count
            set(value) {
                if (field == value) {
                    return
                }
                field = value
                markSet(path.child("count"), value)
            }

        val attrs: MutableMap<String, String> by trackedMap(
            path.child("attrs"),
            attrs,
            queue,
            dirtyTargetProvider = { currentDirtyTarget() },
        )

        override fun toPersistentValue(): Any? {
            return mapOf(
                "id" to id,
                "count" to count,
                "attrs" to persistentValueOf(attrs),
            )
        }
    }

    private class DeepContainerTrackedEntity(
        ctx: TrackContext,
        deep: MutableMap<String, MutableList<MutableMap<Int, MutableList<String>>>>,
    ) {
        val deep: MutableMap<String, MutableList<MutableMap<Int, MutableList<String>>>> by trackedMap(
            ctx.path("deep"),
            deep,
            ctx.queue,
        )
    }

    private data class ImmutableProfile(
        val level: Int,
        val title: String,
    )

    private class ImmutableFieldTrackedEntity(
        ctx: TrackContext,
        val profile: ImmutableProfile,
    ) : PersistentValue {
        @Suppress("unused")
        private val context = ctx

        override fun toPersistentValue(): Any? {
            return mapOf(
                "level" to profile.level,
                "title" to profile.title,
            )
        }
    }
}
