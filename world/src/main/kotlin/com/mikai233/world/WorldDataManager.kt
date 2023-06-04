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
    private val db = ActorDatabase(worldActor.koin, coroutine)
    val worldActionMem = WorldActionMem()

    data class LoadedData(val worldAction: WorldAction)

    override fun loadAll() {
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
        logger.info("worldId:{} data load complete", worldActor.worldId)
        worldActor.context.self tell WorldInitDone
    }

    fun tickDatabase() {
        db.traceDatabase.tick(Clock.System.now())
    }

    fun stopAndFlush(): Boolean {
        return db.stopAndFlushAll()
    }
}