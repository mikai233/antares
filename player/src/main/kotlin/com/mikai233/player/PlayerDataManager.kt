package com.mikai233.player

import com.mikai233.common.core.ShardEntityType
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.common.extension.tryCatchSuspend
import com.mikai233.common.message.player.PlayerInitialized
import io.github.mikai233.asteria.core.EntityKind
import io.github.mikai233.asteria.core.ServiceRegistry
import io.github.mikai233.asteria.persistence.DataManager
import io.github.mikai233.asteria.persistence.DataScope
import io.github.mikai233.asteria.persistence.MemData
import kotlinx.coroutines.CoroutineExceptionHandler
import org.springframework.data.mongodb.core.MongoTemplate
import kotlin.reflect.KClass

class PlayerDataManager(private val player: PlayerActor) {
    private val logger = logger()

    private val dataManager = DataManager(
        DataScope(
            entityKind = EntityKind(ShardEntityType.PlayerActor.name),
            entityId = player.playerId,
            services = ServiceRegistry().apply {
                register(MongoTemplate::class, player.node.mongoDB.mongoTemplate)
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
