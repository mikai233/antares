package com.mikai233.world.data

import com.mikai233.common.core.component.ActorDatabase
import com.mikai233.common.db.MemData
import com.mikai233.common.entity.PlayerAbstract
import com.mikai233.world.WorldActor
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.where

class PlayerAbstractMem : MemData<WorldActor, List<PlayerAbstract>> {
    private lateinit var worldActor: WorldActor
    private val playerAbstracts: MutableMap<Long, PlayerAbstract> = mutableMapOf()
    private val accountToAbstracts: MutableMap<String, PlayerAbstract> = mutableMapOf()

    override fun load(actor: WorldActor, mongoTemplate: MongoTemplate): List<PlayerAbstract> {
        return mongoTemplate.find(
            Query.query(where(PlayerAbstract::worldId).`is`(actor.worldId)),
            PlayerAbstract::class.java
        )
    }

    override fun onComplete(actor: WorldActor, db: ActorDatabase, data: List<PlayerAbstract>) {
        worldActor = actor
        data.forEach {
            db.tracer.traceEntity(it)
            playerAbstracts[it.playerId] = it
            accountToAbstracts[it.account] = it
        }
    }

    fun createAbstract(playerAbstract: PlayerAbstract) {
        check(
            playerAbstracts.containsKey(playerAbstract.playerId).not()
        ) { "abstract:${playerAbstract.playerId} already exists" }
        playerAbstracts[playerAbstract.playerId] = playerAbstract
        accountToAbstracts[playerAbstract.account] = playerAbstract
        worldActor.manager.tracer.saveAndTrace(playerAbstract)
    }

    operator fun get(playerId: Long) = playerAbstracts[playerId]

    fun delAbstract(playerAbstract: PlayerAbstract) {
        accountToAbstracts.remove(playerAbstract.account)
        playerAbstracts.remove(playerAbstract.playerId)?.also {
            worldActor.manager.tracer.deleteAndCancelTrace(playerAbstract)
        }
    }

    fun getByAccount(account: String) = accountToAbstracts[account]
}
