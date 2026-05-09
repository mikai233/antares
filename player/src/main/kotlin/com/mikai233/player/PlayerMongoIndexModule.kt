package com.mikai233.player

import com.mikai233.common.db.MongoDB
import com.mikai233.player.entity.ChatMessageLog
import com.mikai233.player.entity.OfflinePrivateChatMessage
import com.mikai233.player.entity.PlayerActionMongo
import com.mikai233.player.entity.PlayerActivityMongo
import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext

class PlayerMongoIndexModule : AsteriaModule {
    override val name: String = "player-mongodb-indexes"

    override suspend fun install(context: ModuleContext) {
        val mongoDB = context.services.get(MongoDB::class)
        mongoDB.ensureAscendingIndex(PlayerActionMongo.COLLECTION, "playerId")
        mongoDB.ensureAscendingIndex(PlayerActivityMongo.COLLECTION, "playerId")
        mongoDB.ensureAscendingIndex(ChatMessageLog.COLLECTION, "sentAt")
        mongoDB.ensureAscendingIndex(OfflinePrivateChatMessage.COLLECTION, "targetPlayerId")
    }
}
