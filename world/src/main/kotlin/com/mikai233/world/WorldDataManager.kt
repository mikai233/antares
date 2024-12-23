package com.mikai233.world

import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.db.DataManager
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.shared.message.WorldInitDone
import kotlinx.coroutines.*
import kotlinx.datetime.Clock

class WorldDataManager(private val world: WorldActor, private val coroutine: ActorCoroutine) :
    DataManager<WorldActor>("com.mikai233.world.data") {
    private val logger = logger()
    private val clock = Clock.System
    private val db = ActorDatabase(world.koin, coroutine)
    val tracer = db.tracer

    override fun loadAll() {
        logger.info("{} start loading data", world.worldId)
        val template = db.mongoHolder.getGameTemplate()
        coroutine.launch(CoroutineExceptionHandler { _, throwable ->
            logger.error("{} loading data failed, world will stop", world.worldId, throwable)
            world.stop()
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
