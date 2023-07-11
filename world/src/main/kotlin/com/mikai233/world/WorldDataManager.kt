package com.mikai233.world

import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.component.ActorDatabase
import com.mikai233.common.db.DataManager
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.tell
import com.mikai233.shared.message.WorldInitDone
import com.mikai233.shared.message.WorldMessage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class WorldDataManager(private val worldActor: WorldActor, private val coroutine: ActorCoroutine) :
    DataManager<WorldActor, WorldMessage>("com.mikai233.world.data") {
    private val logger = logger()
    private val clock = Clock.System
    private val db = ActorDatabase(worldActor.koin, coroutine)
    val traceDatabase get() = db.traceDatabase

    override fun loadAll() {
        logger.info("{} start loading data", worldActor.worldId)
        val template = db.mongoHolder.getGameTemplate()
        coroutine.launch(CoroutineExceptionHandler { _, throwable ->
            logger.error("{} loading data failed, world will stop", worldActor.worldId, throwable)
            worldActor.stop()
        }) {
            managers.forEach { (manager, mem) ->
                val data = withContext(Dispatchers.IO) {
                    mem.load(worldActor, template)
                }
                mem.onComplete(worldActor, db, data!!)
                logger.info("{} load {} complete", worldActor.worldId, manager.simpleName)
            }
        }
    }

    override fun loadComplete() {
        logger.info("{} data load complete", worldActor.worldId)
        worldActor.context.self tell WorldInitDone
    }

    fun tickDatabase() {
        db.traceDatabase.tick(clock.now())
    }

    fun stopAndFlush(): Boolean {
        return db.stopAndFlushAll()
    }
}
