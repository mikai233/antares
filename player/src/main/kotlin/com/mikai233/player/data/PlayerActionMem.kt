package com.mikai233.player.data

import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mikai233.common.constants.PlayerActionType
import com.mikai233.common.db.AsteriaTrackedMemData
import com.mikai233.common.entity.PlayerAction
import com.mikai233.common.entity.PlayerActionMongo
import com.mikai233.common.entity.PlayerActionTracked
import kotlinx.coroutines.flow.toList

class PlayerActionMem(
    private val playerId: Long,
    private val mongoDatabaseProvider: () -> MongoDatabase,
) : AsteriaTrackedMemData<PlayerAction, PlayerActionTracked>(
    PlayerActionMongo.COLLECTION,
    mongoDatabaseProvider,
    PlayerActionMongo::wrap,
) {
    private var maxActionId: Int = 0
    private val playerAction: MutableMap<String, PlayerActionTracked> = mutableMapOf()
    private val playerActionById: MutableMap<Int, PlayerActionTracked> = mutableMapOf()

    override suspend fun load() {
        val actions = mongoDatabaseProvider().getCollection(PlayerActionMongo.COLLECTION, PlayerAction::class.java)
            .find(eq("playerId", playerId), PlayerAction::class.java)
            .toList()
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
