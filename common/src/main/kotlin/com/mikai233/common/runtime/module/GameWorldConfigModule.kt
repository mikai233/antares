package com.mikai233.common.runtime.module

import com.mikai233.common.config.GAME_WORLDS
import com.mikai233.common.config.GameWorldConfig
import io.github.realmlabs.asteria.cluster.pekko.PekkoEntityWaker
import io.github.realmlabs.asteria.config.center.RuntimeConfigRepository
import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.Serializable
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

const val WORLD_WAKE_TASK = "world"

data class GameWorldConfigUpdate(
    val addedWorldIds: Set<Long>,
    val removedWorldIds: Set<Long>,
)

class GameWorldConfigService(
    worldsById: Map<Long, GameWorldConfig>,
) {
    private val worldsRef = AtomicReference(worldsById.sorted())

    val worldsById: Map<Long, GameWorldConfig> get() = worldsRef.get()

    val worldIds: Set<Long> get() = worldsById.keys

    fun replace(worldsById: Map<Long, GameWorldConfig>): GameWorldConfigUpdate {
        val previous = worldsRef.get()
        val current = worldsById.sorted()
        worldsRef.set(current)
        return GameWorldConfigUpdate(
            addedWorldIds = current.keys - previous.keys,
            removedWorldIds = previous.keys - current.keys,
        )
    }

    private fun Map<Long, GameWorldConfig>.sorted(): Map<Long, GameWorldConfig> {
        return toSortedMap()
    }
}

class GameWorldConfigModule : AsteriaModule {
    override val name: String = "game-world-config"

    private val logger = LoggerFactory.getLogger(GameWorldConfigModule::class.java)
    private var watchJob: Job? = null

    override suspend fun install(context: ModuleContext) {
        val repository = context.services.get(RuntimeConfigRepository::class)
        val worlds = repository.loadGameWorldConfigs()
        context.services.register(GameWorldConfigService::class, GameWorldConfigService(worlds))
    }

    override suspend fun start(context: ModuleContext) {
        val repository = context.services.get(RuntimeConfigRepository::class)
        val service = context.services.get(GameWorldConfigService::class)
        watchJob = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            watchGameWorldConfigs(repository, service, context)
        }
    }

    override suspend fun stop(context: ModuleContext) {
        watchJob?.cancelAndJoin()
        watchJob = null
    }

    private suspend fun watchGameWorldConfigs(
        repository: RuntimeConfigRepository,
        service: GameWorldConfigService,
        context: ModuleContext,
    ) {
        while (currentCoroutineContext().isActive) {
            try {
                repository.watchChildren<GameWorldConfig>(GAME_WORLDS, emitInitial = true)
                    .collect { snapshot ->
                        val worlds = snapshot.values.values
                            .map { it.value }
                            .associateBy { it.id }
                        val update = service.replace(worlds)
                        logger.info(
                            "game world configs refreshed count={} added={} removed={}",
                            worlds.size,
                            update.addedWorldIds.size,
                            update.removedWorldIds.size,
                        )
                        wakeAddedWorlds(context, update.addedWorldIds)
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logger.error("game world config watch failed; retrying", error)
                delay(5.seconds)
            }
        }
    }

    private fun wakeAddedWorlds(context: ModuleContext, worldIds: Set<Long>) {
        if (worldIds.isEmpty()) {
            return
        }
        val waker = context.services.find(PekkoEntityWaker::class)
        if (waker == null) {
            logger.info(
                "world waker is not available yet; added worlds will be picked up on waker startup ids={}",
                worldIds,
            )
            return
        }
        waker.wake(WORLD_WAKE_TASK, worldIds.map { it as Serializable })
        logger.info("queued added worlds for wake ids={}", worldIds)
    }

    private suspend fun RuntimeConfigRepository.loadGameWorldConfigs(): Map<Long, GameWorldConfig> {
        return children<GameWorldConfig>(GAME_WORLDS)
            .values
            .values
            .map { it.value }
            .associateBy { it.id }
    }
}
