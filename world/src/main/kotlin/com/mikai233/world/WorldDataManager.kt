package com.mikai233.world

import com.mikai233.common.db.DataManager
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import kotlinx.coroutines.*
import kotlinx.datetime.Clock

class WorldDataManager(private val world: WorldActor) :
    DataManager<WorldActor>("com.mikai233.world.data") {
    private val logger = logger()
    private val clock = Clock.System

    override fun loadAll() {
        logger.info("{} start loading data", world.worldId)
        world.coroutineScope.launch(CoroutineExceptionHandler { _, throwable ->
            logger.error("{} loading data failed, world will stop", world.worldId, throwable)
            world.passvite()
        }) {
            managers.map { (manager, mem) ->
                launch {
                    val data = withContext(Dispatchers.IO) {
                        mem.load(world, template)
                    }
                    mem.onComplete(world, db, data!!)
                    logger.info("{} load {} complete", world.worldId, manager.simpleName)
                }
            }.joinAll()
            loadComplete()
        }
    }

    override fun loadComplete() {
        logger.info("{} data load complete", world.worldId)
        world.context.self tell WorldInitDone
    }

    fun tickDatabase() {
        tracer.tick(clock.now())
    }

    fun stopAndFlush(): Boolean {
        return db.stopAndFlushAll()
    }
}
