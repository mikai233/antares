package com.mikai233.player

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mikai233.common.core.GameEntityKinds
import com.mikai233.common.core.mongoDB
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.common.extension.tryCatchSuspend
import com.mikai233.common.message.player.PlayerInitialized
import io.github.realmlabs.asteria.core.EntityKind
import io.github.realmlabs.asteria.core.ServiceRegistry
import io.github.realmlabs.asteria.persistence.DataManager
import io.github.realmlabs.asteria.persistence.DataScope
import io.github.realmlabs.asteria.persistence.MemData
import kotlinx.coroutines.CoroutineExceptionHandler
import org.springframework.data.mongodb.core.MongoTemplate
import kotlin.reflect.KClass

class PlayerDataManager(private val player: PlayerActor) {
    private val logger = logger()

    private val dataManager = DataManager(
        DataScope(
            entityKind = EntityKind(GameEntityKinds.PlayerActor),
            entityId = player.playerId,
            services = ServiceRegistry().apply {
                register(MongoTemplate::class, player.node.mongoDB.mongoTemplate)
                register(MongoDatabase::class, player.node.mongoDB.database)
            },
        ),
        PlayerDataModules,
    )

    fun init() {
        logger.info("{} start loading data", player.playerId)
        player.launch(
            CoroutineExceptionHandler { _, throwable ->
                logger.error("{} loading data failed, player will stop", player.playerId, throwable)
                player.passivate()
            },
            timeout = null,
        ) {
            dataManager.loadEager()
            logger.info("player:{} data load complete", player.playerId)
            player.self tell PlayerInitialized
        }
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

    fun flush(onComplete: (Boolean) -> Unit) {
        player.launch(timeout = null) {
            onComplete(dataManager.flush())
        }
    }
}
