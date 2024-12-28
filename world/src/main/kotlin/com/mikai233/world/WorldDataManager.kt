package com.mikai233.world

import com.mikai233.common.db.DataManager
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.shared.message.world.WorldInitialized
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class WorldDataManager(private val world: WorldActor) :
    DataManager<WorldActor>("com.mikai233.world.data") {
    private val logger = logger()

    override fun init() {
        logger.info("{} start loading data", world.worldId)
        world.coroutineScope.launch(CoroutineExceptionHandler { _, throwable ->
            logger.error("{} loading data failed, world will stop", world.worldId, throwable)
            world.passivate()
        }) {
            managers.map { (manager, mem) ->
                async(Dispatchers.IO) {
                    mem.init()
                    logger.info("world:{} load {} complete", world.worldId, manager.simpleName)
                }
            }.awaitAll()
            logger.info("world:{} data load complete", world.worldId)
            world.self tell WorldInitialized
        }
    }

    fun tick() {
    }

    fun stopAndFlush(): Boolean {
        TODO()
    }
}
