package com.mikai233.world

import com.mikai233.common.annotation.GenerateLoader
import com.mikai233.common.annotation.LoadMe
import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.component.ActorDatabase
import com.mikai233.common.db.DataManager
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.tell
import com.mikai233.shared.loadAllExt
import com.mikai233.shared.message.WorldInitDone
import com.mikai233.world.data.PlayerAbstractMem
import com.mikai233.world.data.WorldActionMem
import kotlinx.datetime.Clock

@GenerateLoader(WorldActor::class)
class WorldDataManager(
    private val worldActor: WorldActor,
    private val coroutine: ActorCoroutine
) : DataManager {
    private val logger = logger()
    private val clock = Clock.System
    private val db = ActorDatabase(worldActor.koin, coroutine)
    val traceDatabase get() = db.traceDatabase

    @LoadMe
    val worldActionMem = WorldActionMem()

    @LoadMe
    val playerAbstractMem = PlayerAbstractMem()

    override fun loadAll() {
        logger.info("{} start loading data", worldActor.worldId)
        val template = db.mongoHolder.getGameTemplate()
        coroutine.launch {
            loadAllExt(worldActor, db, template)
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
