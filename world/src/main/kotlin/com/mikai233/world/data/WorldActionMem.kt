package com.mikai233.world.data

import com.mikai233.common.constants.WorldActionType
import com.mikai233.common.core.actor.TrackingCoroutineScope
import com.mikai233.common.db.TraceableMemData
import com.mikai233.common.entity.EntityKryoPool
import com.mikai233.common.entity.WorldAction
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.where

class WorldActionMem(
    private val worldId: Long,
    private val mongoTemplate: () -> MongoTemplate,
    coroutineScope: TrackingCoroutineScope,
) : TraceableMemData<String, WorldAction>(WorldAction::class, EntityKryoPool, coroutineScope, mongoTemplate) {
    private var maxActionId: Long = 0
    private val worldAction: MutableMap<String, WorldAction> = mutableMapOf()
    private val worldActionById: MutableMap<Int, WorldAction> = mutableMapOf()

    override fun init() {
        val template = mongoTemplate()
        val actions = template.find<WorldAction>(Query.query(where(WorldAction::worldId).`is`(worldId)))
        actions.forEach {
            val id = it.id.split("_").last().toLong()
            if (id > maxActionId) {
                maxActionId = id
            }
            worldAction[it.id] = it
            worldActionById[it.actionId] = it
        }
    }

    override fun entities(): Map<String, WorldAction> {
        return worldAction
    }

    fun getOrCreateAction(actionId: Int): WorldAction {
        return worldActionById.getOrPut(actionId) {
            val id = "${worldId}_${++maxActionId}"
            val newAction = WorldAction(id, worldId, actionId, 0L, 0L)
            worldAction[id] = newAction
            newAction
        }
    }

    fun getOrCreateAction(type: WorldActionType): WorldAction {
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
