package com.mikai233.world

import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.component.ActorDatabase
import com.mikai233.common.db.DataManager
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.tell
import com.mikai233.shared.entity.WorldAction
import com.mikai233.shared.message.WorldInitDone
import com.mikai233.world.data.WorldActionMem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class WorldDataManager(
    private val worldActor: WorldActor,
    private val coroutine: ActorCoroutine
) : DataManager {
    private val logger = logger()
    private val clock = Clock.System
    private val db = ActorDatabase(worldActor.koin, coroutine)
    val traceDatabase get() = db.traceDatabase
    val worldActionMem = WorldActionMem()

    data class LoadedData(val worldAction: List<WorldAction>)

    override fun loadAll() {
        logger.info("{} start loading data", worldActor.worldId)
        val template = db.mongoHolder.getGameTemplate()
        coroutine.launch {
            val loadedData = withContext(Dispatchers.IO) {
                val action = worldActionMem.load(worldActor, template)
                LoadedData(worldAction = action)
            }
            worldActionMem.onComplete(worldActor, db, loadedData.worldAction)
            loadComplete()
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
