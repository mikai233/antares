package com.mikai233.world

import com.mikai233.common.db.MongoDB
import com.mikai233.world.entity.PlayerAbstractMongo
import com.mikai233.world.entity.WorldActionMongo
import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext

class WorldMongoIndexModule : AsteriaModule {
    override val name: String = "world-mongodb-indexes"

    override suspend fun install(context: ModuleContext) {
        val mongoDB = context.services.get(MongoDB::class)
        mongoDB.ensureAscendingIndex(PlayerAbstractMongo.COLLECTION, "worldId")
        mongoDB.ensureAscendingIndex(WorldActionMongo.COLLECTION, "worldId")
    }
}
