package com.mikai233.world.data

import com.mikai233.common.constants.WorldActionType
import com.mikai233.common.db.AsteriaTrackedMemData
import com.mikai233.common.db.MongoDB
import com.mikai233.common.entity.WorldAction
import com.mikai233.common.entity.WorldActionMongo
import com.mikai233.common.entity.WorldActionTracked
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query

class WorldActionMem(
    private val worldId: Long,
    private val mongoDbProvider: () -> MongoDB,
) : AsteriaTrackedMemData<WorldAction, WorldActionTracked>(
    WorldActionMongo.COLLECTION,
    { mongoDbProvider().database },
    WorldActionMongo::wrap,
) {
    private var maxActionId: Long = 0
    private val worldAction: MutableMap<String, WorldActionTracked> = mutableMapOf()
    private val worldActionById: MutableMap<Int, WorldActionTracked> = mutableMapOf()

    override suspend fun load() {
        val actions = mongoDbProvider().reactiveTemplate
            .find(query(where("worldId").`is`(worldId)), WorldAction::class.java, WorldActionMongo.COLLECTION)
            .collectList()
            .awaitSingle()
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
