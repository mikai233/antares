package com.mikai233.common.core

import com.mikai233.common.broadcast.PlayerBroadcastActor
import com.mikai233.common.broadcast.PlayerBroadcastEventBus
import com.mikai233.common.config.DATA_SOURCE_GAME
import com.mikai233.common.config.GAME_CONFIG_PUBLICATION
import com.mikai233.common.config.GAME_WORLDS
import com.mikai233.common.config.GameWorldConfig
import com.mikai233.common.config.luban.GameConfigPublicationZipLoader
import com.mikai233.common.config.luban.GameConfigSnapshotLoader
import com.mikai233.common.db.MongoDB
import com.mikai233.common.event.GameConfigChangedEvent
import io.github.realmlabs.asteria.cluster.pekko.PekkoEntityWaker
import io.github.realmlabs.asteria.config.ConfigModule
import io.github.realmlabs.asteria.config.center.ConfigCenterReloadTrigger
import io.github.realmlabs.asteria.config.center.ConfigStore
import io.github.realmlabs.asteria.config.center.ConfigWatchMode
import io.github.realmlabs.asteria.config.center.RuntimeConfigRepository
import io.github.realmlabs.asteria.config.publisher.ConfigPublicationLayout
import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.*
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.routing.FromConfig
import org.slf4j.LoggerFactory
import java.io.Serializable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

class PekkoCoroutineScopeModule : AsteriaModule {
    override val name: String = "pekko-coroutine-scope"

    override suspend fun start(context: ModuleContext) {
        val system = context.services.get(ActorSystem::class)
        val scope = CoroutineScope(system.dispatcher.asCoroutineDispatcher() + SupervisorJob())
        context.services.register(CoroutineScope::class, scope)
    }

    override suspend fun stop(context: ModuleContext) {
        context.services.find(CoroutineScope::class)?.cancel()
    }
}

class PrometheusMetricsModule(
    private val port: Int,
) : AsteriaModule {
    override val name: String = "prometheus-metrics"

    private val logger = LoggerFactory.getLogger(PrometheusMetricsModule::class.java)
    private var server: HTTPServer? = null

    override suspend fun start(context: ModuleContext) {
        if (defaultExportsInitialized.compareAndSet(false, true)) {
            DefaultExports.initialize()
        }
        server = HTTPServer(port)
        logger.info("Prometheus metrics available at http://localhost:{}/metrics", port)
    }

    override suspend fun stop(context: ModuleContext) {
        server?.close()
        server = null
    }

    private companion object {
        val defaultExportsInitialized = AtomicBoolean(false)
    }
}

class MongoDbModule : AsteriaModule {
    override val name: String = "game-mongodb"

    override suspend fun install(context: ModuleContext) {
        val repository = context.services.get(RuntimeConfigRepository::class)
        val config = repository.get<com.mikai233.common.config.DataSourceConfig>(DATA_SOURCE_GAME)?.value
            ?: error("runtime config $DATA_SOURCE_GAME not found")
        context.services.register(MongoDB::class, MongoDB(config))
    }
}

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

class GameConfigModule : AsteriaModule {
    override val name: String = "game-config"

    private val logger = LoggerFactory.getLogger(GameConfigModule::class.java)
    private var delegate: AsteriaModule? = null

    override suspend fun install(context: ModuleContext) {
        val store = context.services.get(ConfigStore::class)
        delegate = ConfigModule {
            loader(
                GameConfigSnapshotLoader(
                    GameConfigPublicationZipLoader(
                        store = store,
                        layout = ConfigPublicationLayout(GAME_CONFIG_PUBLICATION),
                    ),
                ),
            )
            onReload { result ->
                val event = result.changeEventOrNull() ?: return@onReload
                context.services.find(ActorSystem::class)?.eventStream?.publish(GameConfigChangedEvent.from(event))
            }
            hotReload {
                trigger(
                    ConfigCenterReloadTrigger(
                        store = store,
                        path = ConfigPublicationLayout(GAME_CONFIG_PUBLICATION).currentPath,
                        mode = ConfigWatchMode.Value,
                    ),
                )
                onFailure { event ->
                    logger.error("game config hot reload failed", event.error)
                }
            }
        }.also { it.install(context) }
    }

    override suspend fun start(context: ModuleContext) {
        delegate?.start(context)
    }

    override suspend fun stop(context: ModuleContext) {
        delegate?.stop(context)
        delegate = null
    }
}

class PlayerBroadcastRuntime(
    val eventBus: PlayerBroadcastEventBus,
    val router: ActorRef,
)

class PlayerBroadcastModule : AsteriaModule {
    override val name: String = "player-broadcast"

    override suspend fun install(context: ModuleContext) {
        context.services.register(PlayerBroadcastEventBus::class, PlayerBroadcastEventBus())
    }

    override suspend fun start(context: ModuleContext) {
        val system = context.services.get(ActorSystem::class)
        val eventBus = context.services.get(PlayerBroadcastEventBus::class)
        system.actorOf(PlayerBroadcastActor.props(eventBus), PlayerBroadcastActor.NAME)
        val router = system.actorOf(FromConfig.getInstance().props(), "broadcastRouter")
        context.services.register(PlayerBroadcastRuntime::class, PlayerBroadcastRuntime(eventBus, router))
    }
}
