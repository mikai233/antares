package com.mikai233.world

import com.mikai233.common.core.ShardEntityType
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.common.extension.tryCatchSuspend
import com.mikai233.common.message.world.WorldInitialized
import io.github.mikai233.asteria.core.EntityKind
import io.github.mikai233.asteria.core.ServiceRegistry
import io.github.mikai233.asteria.persistence.DataManager
import io.github.mikai233.asteria.persistence.DataScope
import io.github.mikai233.asteria.persistence.MemData
import kotlinx.coroutines.CoroutineExceptionHandler
import org.springframework.data.mongodb.core.MongoTemplate
import kotlin.reflect.KClass


class WorldDataManager(private val world: WorldActor) {
    private val logger = logger()

    private val dataManager = DataManager(
        DataScope(
            entityKind = EntityKind(ShardEntityType.WorldActor.name),
            entityId = world.worldId,
            services = ServiceRegistry().apply {
                register(MongoTemplate::class, world.node.mongoDB.mongoTemplate)
            },
        ),
        WorldDataModules,
    )

    fun init() {
        logger.info("{} start loading data", world.worldId)
        world.launch(
            CoroutineExceptionHandler { _, throwable ->
                logger.error("{} loading data failed, world will stop", world.worldId, throwable)
                world.passivate()
            },
        ) {
            dataManager.loadEager()
            logger.info("world:{} data load complete", world.worldId)
            world.self tell WorldInitialized
        }
    }

    fun <T : MemData> get(type: KClass<T>): T {
        return dataManager.requireLoaded(type)
    }

    inline fun <reified T : MemData> get(): T {
        return get(T::class)
    }

    fun tick() {
        world.launch(timeout = null) {
            tryCatchSuspend(logger) {
                dataManager.tick()
            }
        }
    }

    fun flush(onComplete: (Boolean) -> Unit) {
        world.launch(timeout = null) {
            onComplete(dataManager.flush())
        }
    }
}
