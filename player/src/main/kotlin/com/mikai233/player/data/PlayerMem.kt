package com.mikai233.player.data

import com.mikai233.common.core.actor.TrackingCoroutineScope
import com.mikai233.common.db.TraceableMemData
import com.mikai233.common.serde.KryoPool
import com.mikai233.shared.entity.Player
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findById

class PlayerMem(
    private val playerId: Long,
    private val mongoTemplate: MongoTemplate,
    kryoPool: KryoPool,
    coroutineScope: TrackingCoroutineScope,
) : TraceableMemData<Long, Player>(Player::class, kryoPool, coroutineScope, { mongoTemplate }) {
    lateinit var player: Player
        private set

    override fun init() {
        player = requireNotNull(mongoTemplate.findById<Player>(playerId)) { "cannot find player:$playerId in database" }
    }

    override fun entities(): Map<Long, Player> {
        return if (!this::player.isInitialized) {
            emptyMap()
        } else {
            mapOf(player.id to player)
        }
    }

    fun initPlayer(player: Player) {
        check(!this::player.isInitialized) { "player:$playerId already initialized" }
        this.player = player
    }
}
