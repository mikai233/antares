package com.mikai233.global.data

import com.mikai233.common.db.MemData
import com.mikai233.shared.entity.WorldUid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.data.mongodb.core.MongoTemplate

class WorldUidMem(private val mongoTemplate: () -> MongoTemplate) : MemData<WorldUid> {
    private val uidByWorld: MutableMap<Long, WorldUid> = mutableMapOf()

    private var maxUid = 0

    override fun init() {
        val template = mongoTemplate()
        val worldUidList = template.findAll(WorldUid::class.java)
        worldUidList.forEach {
            uidByWorld[it.worldId] = it
            if (it.uid > maxUid) {
                maxUid = it.uid
            }
        }
    }

    fun getUidByWorld(worldId: Long): WorldUid? {
        return uidByWorld[worldId]
    }

    suspend fun insert(worldId: Long): WorldUid {
        check(!uidByWorld.containsKey(worldId)) { "worldId $worldId already exists" }
        val worldUid = WorldUid(worldId, ++maxUid)
        val template = mongoTemplate()
        withContext(Dispatchers.IO) { template.insert(worldUid) }
        uidByWorld[worldUid.worldId] = worldUid
        return worldUid
    }
}