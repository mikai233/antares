package com.mikai233.player

import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.component.ActorDatabase
import com.mikai233.common.db.DataManager
import com.mikai233.common.db.MemData
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.tell
import com.mikai233.shared.message.PlayerInitDone
import com.mikai233.shared.message.PlayerMessage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.reflections.Reflections
import kotlin.reflect.KClass

class PlayerDataManager(private val playerActor: PlayerActor, private val coroutine: ActorCoroutine) : DataManager {
    private val logger = logger()
    private val clock = Clock.System
    private val db = ActorDatabase(playerActor.koin, coroutine)
    val traceDatabase get() = db.traceDatabase

    val managers: MutableMap<KClass<out MemData<PlayerActor, PlayerMessage, *>>, MemData<PlayerActor, PlayerMessage, in Any>> =
        mutableMapOf()

    init {
        Reflections("com.mikai233.player.data").getSubTypesOf(MemData::class.java).forEach {
            @Suppress("UNCHECKED_CAST")
            val clazz = it.kotlin as KClass<out MemData<PlayerActor, PlayerMessage, *>>
            val constructor = it.getConstructor()
            @Suppress("UNCHECKED_CAST")
            managers[clazz] = constructor.newInstance() as MemData<PlayerActor, PlayerMessage, in Any>
        }
    }

    override fun loadAll() {
        logger.info("{} start loading data", playerActor.playerId)
        val template = db.mongoHolder.getGameTemplate()
        coroutine.launch(CoroutineExceptionHandler { _, throwable ->
            logger.error("{} loading data failed, player will stop", playerActor.playerId, throwable)
            playerActor.stopSelf()
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

    inline fun <reified M : MemData<PlayerActor, PlayerMessage, *>> get(): M {
        return requireNotNull(managers[M::class]) { "manager:${M::class} not found" } as M
    }

    fun tickDatabase() {
        db.traceDatabase.tick(clock.now())
    }

    fun stopAndFlush(): Boolean {
        return db.stopAndFlushAll()
    }
}
