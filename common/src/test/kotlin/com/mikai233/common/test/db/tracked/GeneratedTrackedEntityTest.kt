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
        tracked.title = "veteran"
        tracked.level = 2
        tracked.settings.volume = 5
        tracked.settings.notes.add("loud")
        tracked.tags.add("pvp")
        tracked.optionalTags?.add("duo")
        tracked.attrs["power"] = 12
        tracked.flags.add("arena")
        tracked.pending.addLast(3)
        tracked.scores[1] = 9

        val write = queue.snapshot().single()
        assertEquals("profile", write.key.slot)
        assertEquals(7, write.key.bucket)
        assertEquals("profile-1", write.key.entityId)
        assertEquals("new-name", write.sets["data.nickname"])
        assertEquals("veteran", write.sets["data.title"])
        assertEquals(2, write.sets["data.level"])
        assertEquals(5, write.sets["data.settings.volume"])
        assertEquals(listOf("quiet", "loud"), write.sets["data.settings.notes"])
        assertEquals(listOf("pve", "pvp"), write.sets["data.tags"])
        assertEquals(listOf("solo", "duo"), write.sets["data.optionalTags"])
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
        tracked.title = "veteran"
        tracked.settings.volume = 6
        tracked.settings.notes.add("loud")
        tracked.tags.add("pvp")
        tracked.optionalTags?.add("duo")
        tracked.scores[1] = 9

        val entity = tracked.toEntity()
        assertEquals("profile-1", entity.id)
        assertEquals("account-1", entity.account)
        assertEquals("new-name", entity.nickname)
        assertEquals("veteran", entity.title)
        assertEquals("story", entity.settings.mode)
        assertEquals(6, entity.settings.volume)
        assertEquals(listOf("quiet", "loud"), entity.settings.notes)
        assertEquals(listOf("pve", "pvp"), entity.tags)
        assertEquals(listOf("solo", "duo"), entity.optionalTags)
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
        assertEquals(
            mapOf(
                "mode" to "story",
                "volume" to 3,
                "notes" to listOf("quiet"),
            ),
            value["settings"],
        )
    }

    @Test
    fun `generated nullable collection property records replacements`() {
        val queue = PendingWriteQueue()
        val tracked = GeneratedProfileTrackedFactory.wrap("profile", 7, GeneratedProfile.sample(), queue, "data")

        tracked.optionalTags = null
        tracked.optionalTags = mutableListOf("team")

        val write = queue.snapshot().single()
        assertEquals(listOf("team"), write.sets["data.optionalTags"])
    }
}
