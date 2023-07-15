package com.mikai233.player.data

import com.mikai233.common.core.component.ActorDatabase
import com.mikai233.common.db.MemData
import com.mikai233.player.PlayerActor
import com.mikai233.shared.entity.Player
import org.springframework.data.mongodb.core.MongoTemplate

class PlayerMem : MemData<PlayerActor, Player> {
    private lateinit var playerActor: PlayerActor
    lateinit var player: Player
        private set

    override fun load(actor: PlayerActor, mongoTemplate: MongoTemplate): Player {
        return requireNotNull(
            mongoTemplate.findById(
                actor.playerId,
                Player::class.java
            )
        ) { "cannot find player:${actor.playerId} in database" }
    }

    override fun onComplete(actor: PlayerActor, db: ActorDatabase, data: Player) {
        playerActor = actor
        player = data
        db.tracer.traceEntity(player)
    }

    fun initPlayer(playerActor: PlayerActor, player: Player) {
        check(this::player.isInitialized.not()) { "player already initialized" }
        this.playerActor = playerActor
        this.player = player
        playerActor.manager.tracer.saveAndTrace(player)
    }
}
