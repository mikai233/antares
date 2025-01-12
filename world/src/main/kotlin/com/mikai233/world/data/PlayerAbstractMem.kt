package com.mikai233.world.data

import com.mikai233.common.core.actor.TrackingCoroutineScope
import com.mikai233.common.db.TraceableMemData
import com.mikai233.common.entity.EntityKryoPool
import com.mikai233.common.entity.PlayerAbstract
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.where

class PlayerAbstractMem(
    private val worldId: Long,
    private val mongoTemplate: () -> MongoTemplate,
    coroutineScope: TrackingCoroutineScope,
) :
    TraceableMemData<Long, PlayerAbstract>(PlayerAbstract::class, EntityKryoPool, coroutineScope, mongoTemplate) {
    private val playerAbstracts: MutableMap<Long, PlayerAbstract> = mutableMapOf()
    private val accountToAbstracts: MutableMap<String, PlayerAbstract> = mutableMapOf()

    override fun init() {
        val template = mongoTemplate()
        val playerAbstractList =
            template.find<PlayerAbstract>(Query.query(where(PlayerAbstract::worldId).`is`(worldId)))
        playerAbstractList.forEach {
            playerAbstracts[it.playerId] = it
            accountToAbstracts[it.account] = it
        }
    }

    override fun entities(): Map<Long, PlayerAbstract> {
        return playerAbstracts
    }

    fun addAbstract(abstract: PlayerAbstract) {
        check(playerAbstracts.containsKey(abstract.playerId).not()) { "abstract:${abstract.playerId} already exists" }
        playerAbstracts[abstract.playerId] = abstract
        accountToAbstracts[abstract.account] = abstract
    }

    fun delAbstract(playerAbstract: PlayerAbstract) {
        accountToAbstracts.remove(playerAbstract.account)
        playerAbstracts.remove(playerAbstract.playerId)
    }

    operator fun get(playerId: Long) = playerAbstracts[playerId]

    fun getByAccount(account: String) = accountToAbstracts[account]
}
