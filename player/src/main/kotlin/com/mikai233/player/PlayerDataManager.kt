package com.mikai233.player

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mikai233.common.db.MongoDB
import com.mikai233.common.core.GameEntityKinds
import com.mikai233.common.core.mongoDB
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tryCatchSuspend
import io.github.realmlabs.asteria.core.EntityKind
import io.github.realmlabs.asteria.core.ServiceRegistry
import io.github.realmlabs.asteria.persistence.DataManager
import io.github.realmlabs.asteria.persistence.DataScope
import io.github.realmlabs.asteria.persistence.MemData
import kotlin.reflect.KClass
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

class PlayerDataManager(private val player: PlayerActor) {
    private val logger = logger()

    private val dataManager = DataManager(
        DataScope(
            entityKind = EntityKind(GameEntityKinds.PlayerActor),
            entityId = player.playerId,
            services = ServiceRegistry().apply {
                register(MongoDB::class, player.node.mongoDB)
                register(MongoDatabase::class, player.node.mongoDB.database)
                register(ReactiveMongoTemplate::class, player.node.mongoDB.reactiveTemplate)
            },
        ),
        PlayerDataModules,
    )

    suspend fun load() {
        logger.info("{} start loading data", player.playerId)
        dataManager.loadEager()
        logger.info("player:{} data load complete", player.playerId)
    }

    fun <T : MemData> get(type: KClass<T>): T {
        return dataManager.requireLoaded(type)
    }

    inline fun <reified T : MemData> get(): T {
        return get(T::class)
    }

    fun tick() {
        player.launch(timeout = null) {
            tryCatchSuspend(logger) {
                dataManager.tick()
            }
        }
    }

    suspend fun flush(): Boolean {
        return dataManager.flush()
    }
}
