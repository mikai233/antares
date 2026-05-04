package com.mikai233.common.core

import com.mikai233.common.broadcast.PlayerBroadcastActor
import com.mikai233.common.broadcast.PlayerBroadcastEventBus
import com.mikai233.common.config.DATA_SOURCE_GAME
import com.mikai233.common.config.GameWorldConfig
import com.mikai233.common.config.GAME_CONFIG_PUBLICATION
import com.mikai233.common.config.GAME_WORLDS
import com.mikai233.common.config.luban.GameConfigSnapshotLoader
import com.mikai233.common.config.luban.GameTables
import com.mikai233.common.db.MongoDB
import com.mikai233.common.event.GameConfigChangedEvent
import io.github.realmlabs.asteria.config.ConfigModule
import io.github.realmlabs.asteria.config.center.ConfigCenterReloadTrigger
import io.github.realmlabs.asteria.config.center.ConfigStore
import io.github.realmlabs.asteria.config.center.ConfigWatchMode
import io.github.realmlabs.asteria.config.center.RuntimeConfigRepository
import io.github.realmlabs.asteria.config.publisher.ConfigPublicationLayout
import io.github.realmlabs.asteria.config.publisher.ConfigPublicationLubanBinaryLoader
import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.routing.FromConfig
import org.slf4j.LoggerFactory

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

class GameWorldConfigService(
    worldsById: Map<Long, GameWorldConfig>,
) {
    val worldsById: Map<Long, GameWorldConfig> = worldsById.toSortedMap()

    val worldIds: Set<Long> get() = worldsById.keys
}

class GameWorldConfigModule : AsteriaModule {
    override val name: String = "game-world-config"

    override suspend fun install(context: ModuleContext) {
        val repository = context.services.get(RuntimeConfigRepository::class)
        val worlds = repository.children<GameWorldConfig>(GAME_WORLDS)
            .values
            .values
            .map { it.value }
            .associateBy { it.id }
        context.services.register(GameWorldConfigService::class, GameWorldConfigService(worlds))
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
                    ConfigPublicationLubanBinaryLoader(
                        tablesType = GameTables::class,
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
