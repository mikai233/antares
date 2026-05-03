package com.mikai233.player.data

import com.mikai233.common.db.AsteriaTrackedMemData
import com.mikai233.common.db.MongoDB
import com.mikai233.common.entity.Player
import com.mikai233.common.entity.PlayerMongo
import com.mikai233.common.entity.PlayerTracked
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query

class PlayerMem(
    private val playerId: Long,
    private val mongoDbProvider: () -> MongoDB,
) : AsteriaTrackedMemData<Player, PlayerTracked>(PlayerMongo.COLLECTION, { mongoDbProvider().database }, PlayerMongo::wrap) {
    lateinit var player: PlayerTracked
        private set

    override suspend fun load() {
        val player = mongoDbProvider().reactiveTemplate
            .findOne(query(where("_id").`is`(playerId)), Player::class.java, PlayerMongo.COLLECTION)
            .awaitSingleOrNull()
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
