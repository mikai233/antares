package com.mikai233.player.data

import com.mikai233.common.constants.PlayerActionType
import com.mikai233.common.db.tracked.TrackedMemData
import com.mikai233.common.entity.PlayerAction
import com.mikai233.common.entity.tracked.PlayerActionTracked
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.where

class PlayerActionMem(
    private val playerId: Long,
    private val mongoTemplate: () -> MongoTemplate,
) : TrackedMemData<PlayerAction, PlayerActionTracked>(
    "player_action",
    0,
    mongoTemplate,
    id = { it.id },
    factory = ::PlayerActionTracked,
) {
    private var maxActionId: Int = 0
    private val playerAction: MutableMap<String, PlayerActionTracked> = mutableMapOf()
    private val playerActionById: MutableMap<Int, PlayerActionTracked> = mutableMapOf()

    override fun init() {
        val template = mongoTemplate()
        val actions = template.find<PlayerAction>(Query.query(where(PlayerAction::playerId).`is`(playerId)))
        actions.forEach {
            val id = it.id.split("_").last().toInt()
            if (id > maxActionId) {
                maxActionId = id
            }
            val tracked = attachLoaded(it)
            playerAction[it.id] = tracked
            playerActionById[it.actionId] = tracked
        }
    }

    fun entities(): Map<String, PlayerActionTracked> {
        return playerAction
    }

    fun getOrCreateAction(actionId: Int): PlayerActionTracked {
        return playerActionById.getOrPut(actionId) {
            val id = "${playerId}_${++maxActionId}"
            val newAction = PlayerAction(id, playerId, actionId, 0L, 0L)
            val tracked = createTracked(newAction)
            playerAction[id] = tracked
            tracked
        }
    }

    fun getOrCreateAction(type: PlayerActionType): PlayerActionTracked {
        return getOrCreateAction(type.id)
    }
}
