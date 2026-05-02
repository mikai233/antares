package com.mikai233.player.data

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mikai233.common.db.AsteriaTrackedMemData
import com.mikai233.common.entity.Player
import com.mikai233.common.entity.PlayerMongo
import com.mikai233.common.entity.PlayerTracked
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findById

class PlayerMem(
    private val playerId: Long,
    private val mongoTemplate: () -> MongoTemplate,
    mongoDatabase: () -> MongoDatabase,
) : AsteriaTrackedMemData<Player, PlayerTracked>(PlayerMongo.COLLECTION, mongoDatabase, PlayerMongo::wrap) {
    lateinit var player: PlayerTracked
        private set

    override suspend fun load() {
        val template = mongoTemplate()
        val player = template.findById<Player>(playerId)
        if (player != null) {
            check(!this::player.isInitialized) { "player:$playerId already initialized" }
            this.player = attachLoaded(player)
        }
    }

    fun entities(): Map<Long, PlayerTracked> {
        return if (!this::player.isInitialized) {
            emptyMap()
        } else {
            mapOf(player.id to player)
        }
    }

    fun initPlayer(player: Player) {
        check(!this::player.isInitialized) { "player:$playerId already initialized" }
        val tracked = createTracked(player)
        this.player = tracked
    }
}
