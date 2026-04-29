package com.mikai233.player.data

import com.mikai233.common.db.tracked.TrackedMemData
import com.mikai233.common.entity.Player
import com.mikai233.common.entity.tracked.PlayerTracked
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findById

class PlayerMem(
    private val playerId: Long,
    private val mongoTemplate: () -> MongoTemplate,
) : TrackedMemData<Player, PlayerTracked>(
    "player",
    0,
    mongoTemplate,
    id = { it.id },
    factory = ::PlayerTracked,
) {
    lateinit var player: PlayerTracked
        private set

    override fun init() {
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
