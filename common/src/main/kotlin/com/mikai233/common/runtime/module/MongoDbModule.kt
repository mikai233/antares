package com.mikai233.common.runtime.module

import com.mikai233.common.config.DATA_SOURCE_GAME
import com.mikai233.common.config.DataSourceConfig
import com.mikai233.common.db.MongoDB
import io.github.realmlabs.asteria.config.center.RuntimeConfigRepository
import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext

class MongoDbModule : AsteriaModule {
    override val name: String = "game-mongodb"

    override suspend fun install(context: ModuleContext) {
        val repository = context.services.get(RuntimeConfigRepository::class)
        val config = repository.get<DataSourceConfig>(DATA_SOURCE_GAME)?.value
            ?: error("runtime config $DATA_SOURCE_GAME not found")
        context.services.register(MongoDB::class, MongoDB(config))
    }
}
