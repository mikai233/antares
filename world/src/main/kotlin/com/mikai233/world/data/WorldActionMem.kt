package com.mikai233.world.data

import com.mikai233.common.core.component.ActorDatabase
import com.mikai233.common.db.MemData
import com.mikai233.shared.constants.WorldActionType
import com.mikai233.shared.entity.WorldAction
import com.mikai233.world.WorldActor
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.where

class WorldActionMem : MemData<WorldActor, List<WorldAction>> {
    private lateinit var worldActor: WorldActor
    private var maxActionId: Long = 0
    private val worldAction: MutableMap<Int, WorldAction> = mutableMapOf()

    override fun load(actor: WorldActor, mongoTemplate: MongoTemplate): List<WorldAction> {
        return mongoTemplate.find(Query.query(where(WorldAction::worldId).`is`(actor.worldId)), WorldAction::class.java)
    }

    override fun onComplete(actor: WorldActor, db: ActorDatabase, data: List<WorldAction>) {
        worldActor = actor
        data.forEach {
            db.tracer.traceEntity(it)
            val id = it.id.split("_").last().toLong()
            if (id > maxActionId) {
                maxActionId = id
            }
            worldAction[it.actionId] = it
        }
    }

    fun getOrCreateAction(actionId: Int): WorldAction {
        val action = worldAction[actionId]
        return if (action != null) {
            action
        } else {
            val id = "${worldActor.worldId}_${++maxActionId}"
            val newAction = WorldAction(id, worldActor.worldId, actionId, 0L, 0L)
            worldAction[actionId] = newAction
            worldActor.manager.tracer.saveAndTrace(newAction)
            newAction
        }
    }

    fun getOrCreateAction(type: WorldActionType): WorldAction {
        return getOrCreateAction(type.id)
    }

    fun delAction(actionId: Int) {
        worldAction.remove(actionId)?.also {
            worldActor.manager.tracer.deleteAndCancelTrace(it)
        }
    }

    fun delAction(type: WorldActionType) {
        delAction(type.id)
    }
}
