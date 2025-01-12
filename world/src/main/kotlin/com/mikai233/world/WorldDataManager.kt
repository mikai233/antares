package com.mikai233.world

import com.mikai233.common.db.DataManager
import com.mikai233.common.db.TraceableMemData
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.common.extension.tryCatch
import com.mikai233.common.message.world.WorldInitialized
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlin.reflect.full.primaryConstructor


class WorldDataManager(private val world: WorldActor) : DataManager<WorldActor>() {
    private val logger = logger()

    private val traceableMemData: MutableList<TraceableMemData<*, *>> = mutableListOf()

    override fun init() {
        MemImpl.forEach {
            val primaryConstructor =
                requireNotNull(it.primaryConstructor) { "${it.qualifiedName} primary constructor not found" }
            val mem = primaryConstructor.call(world.worldId, { world.node.mongoDB.mongoTemplate }, world.coroutineScope)
            managers[it] = mem
        }
        logger.info("{} start loading data", world.worldId)
        world.launch(CoroutineExceptionHandler { _, throwable ->
            logger.error("{} loading data failed, world will stop", world.worldId, throwable)
            world.passivate()
        }) {
            managers.map { (manager, mem) ->
                async(Dispatchers.IO) {
                    mem.init()
                    logger.info("world:{} load {} complete", world.worldId, manager.simpleName)
                }
            }.awaitAll()
            managers.values.filterIsInstance<TraceableMemData<*, *>>().forEach {
                it.markCleanup()
                traceableMemData.add(it)
            }
            logger.info("world:{} data load complete", world.worldId)
            world.self tell WorldInitialized
        }
    }

    fun tick() {
        traceableMemData.forEach {
            tryCatch(logger) {
                it.traceEntities()
            }
        }
    }

    fun flush(): Boolean {
        return traceableMemData.all { it.flush() }
    }
}
