package com.mikai233.world.data

import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mikai233.common.constants.WorldActionType
import com.mikai233.common.db.AsteriaTrackedMemData
import com.mikai233.common.entity.WorldAction
import com.mikai233.common.entity.WorldActionMongo
import com.mikai233.common.entity.WorldActionTracked
import kotlinx.coroutines.flow.toList

class WorldActionMem(
    private val worldId: Long,
    private val mongoDatabaseProvider: () -> MongoDatabase,
) : AsteriaTrackedMemData<WorldAction, WorldActionTracked>(
    WorldActionMongo.COLLECTION,
    mongoDatabaseProvider,
    WorldActionMongo::wrap,
) {
    private var maxActionId: Long = 0
    private val worldAction: MutableMap<String, WorldActionTracked> = mutableMapOf()
    private val worldActionById: MutableMap<Int, WorldActionTracked> = mutableMapOf()

    override suspend fun load() {
        val actions = mongoDatabaseProvider().getCollection(WorldActionMongo.COLLECTION, WorldAction::class.java)
            .find(eq("worldId", worldId), WorldAction::class.java)
            .toList()
        actions.forEach {
            val id = it.id.split("_").last().toLong()
            if (id > maxActionId) {
                maxActionId = id
            }
            val tracked = attachLoaded(it)
            worldAction[it.id] = tracked
            worldActionById[it.actionId] = tracked
        }
    }

    fun entities(): Map<String, WorldActionTracked> {
        return worldAction
    }

    fun getOrCreateAction(actionId: Int): WorldActionTracked {
        return worldActionById.getOrPut(actionId) {
            val id = "${worldId}_${++maxActionId}"
            val newAction = WorldAction(id, worldId, actionId, 0L, 0L)
            val tracked = createTracked(newAction)
            worldAction[id] = tracked
            tracked
        }
    }

    fun getOrCreateAction(type: WorldActionType): WorldActionTracked {
        return getOrCreateAction(type.id)
    }

    fun delAction(actionId: Int) {
        worldActionById.remove(actionId)?.also {
            worldAction.remove(it.id)
            removeTracked(it.id)
        }
    }

    fun delAction(type: WorldActionType) {
        delAction(type.id)
    }
}
