package com.mikai233.player

import com.mikai233.common.db.DataManager
import com.mikai233.common.db.TraceableMemData
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.common.extension.tryCatch
import com.mikai233.common.message.player.PlayerInitialized
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlin.reflect.full.primaryConstructor

class PlayerDataManager(private val player: PlayerActor) : DataManager<PlayerActor>() {
    private val logger = logger()

    private val traceableMemData: MutableList<TraceableMemData<*, *>> = mutableListOf()

    override fun init() {
        MemImpl.forEach {
            val primaryConstructor =
                requireNotNull(it.primaryConstructor) { "${it.qualifiedName} primary constructor not found" }
            val mem =
                primaryConstructor.call(player.playerId, { player.node.mongoDB.mongoTemplate }, player.coroutineScope)
            managers[it] = mem
        }
        logger.info("{} start loading data", player.playerId)
        player.launch(
            CoroutineExceptionHandler { _, throwable ->
                logger.error("{} loading data failed, player will stop", player.playerId, throwable)
                player.passivate()
            },
        ) {
            managers.map { (manager, mem) ->
                async(Dispatchers.IO) {
                    mem.init()
                    logger.info("player:{} load {} complete", player.playerId, manager.simpleName)
                }
            }.awaitAll()
            managers.values.filterIsInstance<TraceableMemData<*, *>>().forEach {
                it.markCleanup()
                traceableMemData.add(it)
            }
            logger.info("player:{} data load complete", player.playerId)
            player.self tell PlayerInitialized
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
