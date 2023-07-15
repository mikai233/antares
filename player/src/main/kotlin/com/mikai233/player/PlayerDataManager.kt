package com.mikai233.player

import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.component.ActorDatabase
import com.mikai233.common.db.DataManager
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.tell
import com.mikai233.shared.message.PlayerInitDone
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class PlayerDataManager(private val player: PlayerActor, private val coroutine: ActorCoroutine) :
    DataManager<PlayerActor>("com.mikai233.player.data") {
    private val logger = logger()
    private val clock = Clock.System
    private val db = ActorDatabase(player.koin, coroutine)
    val tracer = db.tracer

    override fun loadAll() {
        logger.info("{} start loading data", player.playerId)
        val template = db.mongoHolder.getGameTemplate()
        coroutine.launch(CoroutineExceptionHandler { _, throwable ->
            logger.error("{} loading data failed, player will stop", player.playerId, throwable)
            player.stop()
        }) {
            managers.forEach { (manager, mem) ->
                val data = withContext(Dispatchers.IO) {
                    mem.load(player, template)
                }
                mem.onComplete(player, db, data!!)
                logger.info("{} load {} complete", player.playerId, manager.simpleName)
            }
        }
    }

    override fun loadComplete() {
        logger.info("{} data load complete", player.playerId)
        player.context.self tell PlayerInitDone
    }

    fun tick() {
        tracer.tick(clock.now())
    }

    fun stopAndFlush(): Boolean {
        return db.stopAndFlushAll()
    }
}
