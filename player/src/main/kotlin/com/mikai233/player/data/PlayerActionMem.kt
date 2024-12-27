package com.mikai233.player.data

import com.mikai233.common.db.MemData
import com.mikai233.player.PlayerActor
import com.mikai233.shared.constants.PlayerActionType
import com.mikai233.shared.entity.PlayerAction
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.where

class PlayerActionMem : MemData<PlayerAction> {
    private lateinit var playerActor: PlayerActor
    private var maxActionId: Long = 0
    private val worldAction: MutableMap<Int, PlayerAction> = mutableMapOf()

    override fun init() {
        TODO("Not yet implemented")
    }

    override fun traceValues(): List<PlayerAction> {

    }

    override fun load(actor: PlayerActor, mongoTemplate: MongoTemplate): List<PlayerAction> {
        return mongoTemplate.find(
            Query.query(where(PlayerAction::playerId).`is`(actor.playerId)),
            PlayerAction::class.java
        )
    }

    override fun onComplete(actor: PlayerActor, db: ActorDatabase, data: List<PlayerAction>) {
        playerActor = actor
        data.forEach {
            db.tracer.traceEntity(it)
            val id = it.id.split("_").last().toLong()
            if (id > maxActionId) {
                maxActionId = id
            }
            worldAction[it.actionId] = it
        }
    }

    fun getOrCreateAction(actionId: Int): PlayerAction {
        val action = worldAction[actionId]
        return if (action != null) {
            action
        } else {
            val id = "${playerActor.playerId}_${++maxActionId}"
            val newAction = PlayerAction(id, playerActor.playerId, actionId, 0L, 0L)
            worldAction[actionId] = newAction
            newAction
        }
    }

    fun getOrCreateAction(type: PlayerActionType): PlayerAction {
        return getOrCreateAction(type.id)
    }

    fun delAction(actionId: Int) {
        worldAction.remove(actionId)
    }

    fun delAction(type: PlayerActionType) {
        delAction(type.id)
    }
}
