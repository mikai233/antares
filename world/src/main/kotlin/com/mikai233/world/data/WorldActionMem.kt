package com.mikai233.world.data

import com.mikai233.common.core.actor.TrackingCoroutineScope
import com.mikai233.common.db.TraceableMemData
import com.mikai233.common.serde.KryoPool
import com.mikai233.shared.constants.WorldActionType
import com.mikai233.shared.entity.WorldAction
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.where

class WorldActionMem(
    private val worldId: Long,
    private val mongoTemplate: MongoTemplate,
    kryoPool: KryoPool,
    coroutineScope: TrackingCoroutineScope,
) : TraceableMemData<Int, WorldAction>(WorldAction::class, kryoPool, coroutineScope, { mongoTemplate }) {
    private var maxActionId: Long = 0
    private val worldAction: MutableMap<Int, WorldAction> = mutableMapOf()

    override fun init() {
        val actions = mongoTemplate.find<WorldAction>(Query.query(where(WorldAction::worldId).`is`(worldId)))
        actions.forEach {
            val id = it.id.split("_").last().toLong()
            if (id > maxActionId) {
                maxActionId = id
            }
            worldAction[it.actionId] = it
        }
    }

    override fun entities(): Map<Int, WorldAction> {
        return worldAction
    }

    fun getOrCreateAction(actionId: Int): WorldAction {
        return worldAction.getOrPut(actionId) {
            val id = "${worldId}_${++maxActionId}"
            WorldAction(id, worldId, actionId, 0L, 0L)
        }
    }

    fun getOrCreateAction(type: WorldActionType): WorldAction {
        return getOrCreateAction(type.id)
    }

    fun delAction(actionId: Int) {
        worldAction.remove(actionId)
    }

    fun delAction(type: WorldActionType) {
        delAction(type.id)
    }
}
