package com.mikai233.player

import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.component.ActorDatabase
import com.mikai233.common.db.DataManager
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.tell
import com.mikai233.player.data.PlayerActionMem
import com.mikai233.player.data.PlayerMem
import com.mikai233.shared.entity.Player
import com.mikai233.shared.entity.PlayerAction
import com.mikai233.shared.message.PlayerInitDone
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class PlayerDataManager(private val playerActor: PlayerActor, private val coroutine: ActorCoroutine) : DataManager {
    private val logger = logger()
    private val clock = Clock.System
    private val db = ActorDatabase(playerActor.koin, coroutine)
    val traceDatabase get() = db.traceDatabase

    //TODO auto generate load code
    val playerMem = PlayerMem()
    val playerActionMem = PlayerActionMem()

    data class LoadedData(val player: Player, val playerAction: List<PlayerAction>)

    override fun loadAll() {
        logger.info("{} start loading data", playerActor.playerId)
        val template = db.mongoHolder.getGameTemplate()
        coroutine.launch(CoroutineExceptionHandler { _, throwable ->
            logger.error("{} loading data failed, player will stop", playerActor.playerId, throwable)
            playerActor.stopSelf()
        }) {
            val loadedData = withContext(Dispatchers.IO) {
                val player = playerMem.load(playerActor, template)
                val playerAction = playerActionMem.load(playerActor, template)
                LoadedData(player, playerAction)
            }
            playerMem.onComplete(playerActor, db, loadedData.player)
            playerActionMem.onComplete(playerActor, db, loadedData.playerAction)
            loadComplete()
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
