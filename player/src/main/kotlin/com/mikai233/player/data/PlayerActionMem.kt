package com.mikai233.player.data

import com.mikai233.common.constants.PlayerActionType
import com.mikai233.common.core.actor.TrackingCoroutineScope
import com.mikai233.common.db.TraceableMemData
import com.mikai233.common.entity.PlayerAction
import com.mikai233.common.excel.GameConfigKryoPool
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.where

class PlayerActionMem(
    private val playerId: Long,
    private val mongoTemplate: () -> MongoTemplate,
    coroutineScope: TrackingCoroutineScope,
) : TraceableMemData<String, PlayerAction>(PlayerAction::class, GameConfigKryoPool, coroutineScope, mongoTemplate) {
    private var maxActionId: Int = 0
    private val playerAction: MutableMap<String, PlayerAction> = mutableMapOf()
    private val playerActionById: MutableMap<Int, PlayerAction> = mutableMapOf()

    override fun init() {
        val template = mongoTemplate()
        val actions = template.find<PlayerAction>(Query.query(where(PlayerAction::playerId).`is`(playerId)))
        actions.forEach {
            val id = it.id.split("_").last().toInt()
            if (id > maxActionId) {
                maxActionId = id
            }
            playerAction[it.id] = it
            playerActionById[it.actionId] = it
        }
    }

    override fun entities(): Map<String, PlayerAction> {
        return playerAction
    }

    fun getOrCreateAction(actionId: Int): PlayerAction {
        return playerActionById.getOrPut(actionId) {
            val id = "${playerId}_${++maxActionId}"
            val newAction = PlayerAction(id, playerId, actionId, 0L, 0L)
            playerAction[id] = newAction
            newAction
        }
    }

    fun getOrCreateAction(type: PlayerActionType): PlayerAction {
        return getOrCreateAction(type.id)
    }
}
