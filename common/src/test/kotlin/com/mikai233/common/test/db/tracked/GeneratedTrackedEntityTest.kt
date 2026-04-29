package com.mikai233.common.test.db.tracked

import com.mikai233.common.db.tracked.PendingWriteQueue
import com.mikai233.common.test.db.tracked.generated.GeneratedProfile
import com.mikai233.common.test.db.tracked.generated.tracked.GeneratedProfileTrackedFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeneratedTrackedEntityTest {
    @Test
    fun `generated tracked entity records scalar and collection changes`() {
        val queue = PendingWriteQueue()
        val tracked = GeneratedProfileTrackedFactory.wrap("profile", 7, GeneratedProfile.sample(), queue, "data")

        tracked.nickname = "new-name"
        tracked.level = 2
        tracked.tags.add("pvp")
        tracked.attrs["power"] = 12
        tracked.flags.add("arena")
        tracked.pending.addLast(3)
        tracked.scores[1] = 9

        val write = queue.snapshot().single()
        assertEquals("profile", write.key.slot)
        assertEquals(7, write.key.bucket)
        assertEquals("profile-1", write.key.entityId)
        assertEquals("new-name", write.sets["data.nickname"])
        assertEquals(2, write.sets["data.level"])
        assertEquals(listOf("pve", "pvp"), write.sets["data.tags"])
        assertEquals(12, write.sets["data.attrs.power"])
        assertEquals(listOf("newbie", "arena"), write.sets["data.flags"])
        assertEquals(listOf(1, 2, 3), write.sets["data.pending"])
        assertEquals(9, write.sets["data.scores.1"])
    }

    @Test
    fun `generated tracked entity can rebuild source entity`() {
        val queue = PendingWriteQueue()
        val tracked = GeneratedProfileTrackedFactory.wrap("profile", 7, GeneratedProfile.sample(), queue, "data")

        tracked.nickname = "new-name"
        tracked.tags.add("pvp")
        tracked.scores[1] = 9

        val entity = tracked.toEntity()
        assertEquals("profile-1", entity.id)
        assertEquals("account-1", entity.account)
        assertEquals("new-name", entity.nickname)
        assertEquals(listOf("pve", "pvp"), entity.tags)
        assertEquals(listOf(3, 9), entity.scores.toList())
    }

    @Test
    fun `generated factory can write root fields without data prefix`() {
        val queue = PendingWriteQueue()
        val tracked = GeneratedProfileTrackedFactory.wrap("profile", 7, GeneratedProfile.sample(), queue)

        tracked.level = 3

        val write = queue.snapshot().single()
        assertEquals("profile-1", write.key.entityId)
        assertEquals(3, write.sets["level"])
    }

    @Test
    fun `generated persistent value stores id as mongo id field`() {
        val queue = PendingWriteQueue()
        val tracked = GeneratedProfileTrackedFactory.wrap("profile", 7, GeneratedProfile.sample(), queue)

        val value = tracked.toPersistentValue() as Map<*, *>
        assertEquals("profile-1", value["_id"])
        assertEquals(null, value["id"])
    }
}
