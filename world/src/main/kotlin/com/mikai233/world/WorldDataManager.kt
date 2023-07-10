package com.mikai233.world

import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.component.ActorDatabase
import com.mikai233.common.db.DataManager
import com.mikai233.common.db.MemData
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.tell
import com.mikai233.shared.message.WorldInitDone
import com.mikai233.shared.message.WorldMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.reflections.Reflections
import kotlin.reflect.KClass

class WorldDataManager(private val worldActor: WorldActor, private val coroutine: ActorCoroutine) : DataManager {
    private val logger = logger()
    private val clock = Clock.System
    private val db = ActorDatabase(worldActor.koin, coroutine)
    val traceDatabase get() = db.traceDatabase
    val managers: MutableMap<KClass<out MemData<WorldActor, WorldMessage, *>>, MemData<WorldActor, WorldMessage, in Any>> =
        mutableMapOf()

    init {
        Reflections("com.mikai233.world.data").getSubTypesOf(MemData::class.java).forEach {
            @Suppress("UNCHECKED_CAST")
            val clazz = it.kotlin as KClass<out MemData<WorldActor, WorldMessage, *>>
            val constructor = it.getConstructor()
            @Suppress("UNCHECKED_CAST")
            managers[clazz] = constructor.newInstance() as MemData<WorldActor, WorldMessage, in Any>
        }
    }

    override fun loadAll() {
        logger.info("{} start loading data", worldActor.worldId)
        val template = db.mongoHolder.getGameTemplate()
        coroutine.launch {
            managers.forEach { (manager, mem) ->
                val data = withContext(Dispatchers.IO) {
                    mem.load(worldActor, template)
                }
                mem.onComplete(worldActor, db, data!!)
                logger.info("{} load {} complete", worldActor.worldId, manager.simpleName)
            }
        }
    }

    inline fun <reified M : MemData<WorldActor, WorldMessage, *>> get(): M {
        return requireNotNull(managers[M::class]) { "manager:${M::class} not found" } as M
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
