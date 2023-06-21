package com.mikai233.player

import com.mikai233.common.annotation.GenerateLoader
import com.mikai233.common.annotation.LoadMe
import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.component.ActorDatabase
import com.mikai233.common.db.DataManager
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.tell
import com.mikai233.player.data.PlayerActionMem
import com.mikai233.player.data.PlayerMem
import com.mikai233.shared.loadAllExt
import com.mikai233.shared.message.PlayerInitDone
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.datetime.Clock

@GenerateLoader(PlayerActor::class)
class PlayerDataManager(private val playerActor: PlayerActor, private val coroutine: ActorCoroutine) : DataManager {
    private val logger = logger()
    private val clock = Clock.System
    private val db = ActorDatabase(playerActor.koin, coroutine)
    val traceDatabase get() = db.traceDatabase

    @LoadMe
    val playerMem = PlayerMem()

    @LoadMe
    val playerActionMem = PlayerActionMem()

    override fun loadAll() {
        logger.info("{} start loading data", playerActor.playerId)
        val template = db.mongoHolder.getGameTemplate()
        coroutine.launch(CoroutineExceptionHandler { _, throwable ->
            logger.error("{} loading data failed, player will stop", playerActor.playerId, throwable)
            playerActor.stopSelf()
        }) {
            loadAllExt(playerActor, db, template)
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
