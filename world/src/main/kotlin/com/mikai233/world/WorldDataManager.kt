package com.mikai233.world

import com.mikai233.common.db.MongoDB
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tryCatchSuspend
import com.mikai233.common.runtime.GameEntityKinds
import com.mikai233.common.runtime.mongoDB
import com.mikai233.common.time.GameTime
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.github.realmlabs.asteria.core.EntityKind
import io.github.realmlabs.asteria.core.ServiceRegistry
import io.github.realmlabs.asteria.persistence.DataManager
import io.github.realmlabs.asteria.persistence.DataScope
import io.github.realmlabs.asteria.persistence.MemData
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import kotlin.reflect.KClass


class WorldDataManager(private val world: WorldActor) {
    private val logger = logger()

    private val dataManager = DataManager(
        DataScope(
            entityKind = EntityKind(GameEntityKinds.WorldActor),
            entityId = world.worldId,
            services = ServiceRegistry().apply {
                register(MongoDB::class, world.node.mongoDB)
                register(MongoDatabase::class, world.node.mongoDB.database)
                register(ReactiveMongoTemplate::class, world.node.mongoDB.reactiveTemplate)
                register(GameTime::class, world.gameTime)
            },
        ),
        WorldDataModules,
    )

    suspend fun load() {
        logger.info("{} start loading data", world.worldId)
        dataManager.loadEager()
        logger.info("world:{} data load complete", world.worldId)
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

    suspend fun flush(): Boolean {
        return dataManager.flush()
    }
}
