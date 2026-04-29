package com.mikai233.world.data

import com.mikai233.common.constants.WorldActionType
import com.mikai233.common.db.tracked.TrackedMemData
import com.mikai233.common.entity.WorldAction
import com.mikai233.common.entity.tracked.WorldActionTracked
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.where

class WorldActionMem(
    private val worldId: Long,
    private val mongoTemplate: () -> MongoTemplate,
) : TrackedMemData<WorldAction, WorldActionTracked>(
    "world_action",
    0,
    mongoTemplate,
    id = { it.id },
    factory = ::WorldActionTracked,
) {
    private var maxActionId: Long = 0
    private val worldAction: MutableMap<String, WorldActionTracked> = mutableMapOf()
    private val worldActionById: MutableMap<Int, WorldActionTracked> = mutableMapOf()

    override fun init() {
        val template = mongoTemplate()
        val actions = template.find<WorldAction>(Query.query(where(WorldAction::worldId).`is`(worldId)))
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
        }
    }

    fun delAction(type: WorldActionType) {
        delAction(type.id)
    }
}
