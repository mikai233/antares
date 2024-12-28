package com.mikai233.player

import com.mikai233.common.core.actor.TrackingCoroutineScope
import com.mikai233.common.db.DataManager
import com.mikai233.common.db.TraceableMemData
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.shared.message.player.PlayerInitialized
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class PlayerDataManager(private val player: PlayerActor, private val coroutine: TrackingCoroutineScope) :
    DataManager<PlayerActor>("com.mikai233.player.data") {
    private val logger = logger()

    override fun init() {
        logger.info("{} start loading data", player.playerId)
        coroutine.launch(CoroutineExceptionHandler { _, throwable ->
            logger.error("{} loading data failed, player will stop", player.playerId, throwable)
            player.passivate()
        }) {
            managers.map { (manager, mem) ->
                async(Dispatchers.IO) {
                    mem.init()
                    logger.info("player:{} load {} complete", player.playerId, manager.simpleName)
                }
            }.awaitAll()
            managers.values.filterIsInstance<TraceableMemData<*, *>>().forEach { it.markCleanup() }
            logger.info("player:{} data load complete", player.playerId)
            player.self tell PlayerInitialized
        }
    }

    fun tick() {

    }

    fun stopAndFlush(): Boolean {
        TODO()
    }
}
