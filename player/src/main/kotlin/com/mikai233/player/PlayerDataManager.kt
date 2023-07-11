package com.mikai233.player

import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.component.ActorDatabase
import com.mikai233.common.db.DataManager
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.tell
import com.mikai233.shared.message.PlayerInitDone
import com.mikai233.shared.message.PlayerMessage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class PlayerDataManager(private val playerActor: PlayerActor, private val coroutine: ActorCoroutine) :
    DataManager<PlayerActor, PlayerMessage>("com.mikai233.player.data") {
    private val logger = logger()
    private val clock = Clock.System
    private val db = ActorDatabase(playerActor.koin, coroutine)
    val traceDatabase get() = db.traceDatabase

    override fun loadAll() {
        logger.info("{} start loading data", playerActor.playerId)
        val template = db.mongoHolder.getGameTemplate()
        coroutine.launch(CoroutineExceptionHandler { _, throwable ->
            logger.error("{} loading data failed, player will stop", playerActor.playerId, throwable)
            playerActor.stop()
        }) {
            managers.forEach { (manager, mem) ->
                val data = withContext(Dispatchers.IO) {
                    mem.load(playerActor, template)
                }
                mem.onComplete(playerActor, db, data!!)
                logger.info("{} load {} complete", playerActor.playerId, manager.simpleName)
            }
        }
    }

    override fun loadComplete() {
        logger.info("{} data load complete", playerActor.playerId)
        playerActor.context.self tell PlayerInitDone
    }

    fun tickDatabase() {
        db.traceDatabase.tick(clock.now())
    }

    fun stopAndFlush(): Boolean {
        return db.stopAndFlushAll()
    }
}
