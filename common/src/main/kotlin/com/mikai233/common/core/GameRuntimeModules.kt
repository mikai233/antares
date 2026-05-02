package com.mikai233.common.core

import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.mikai233.common.broadcast.PlayerBroadcastActor
import com.mikai233.common.broadcast.PlayerBroadcastEventBus
import com.mikai233.common.config.ConfigCache
import com.mikai233.common.config.ConfigChildrenCache
import com.mikai233.common.config.GAME_CONFIG
import com.mikai233.common.config.GAME_WORLDS
import com.mikai233.common.config.GameWorldConfig
import com.mikai233.common.config.GameWorldMeta
import com.mikai233.common.db.MongoDB
import com.mikai233.common.entity.EntityKryoPool
import com.mikai233.common.event.GameConfigUpdateEvent
import com.mikai233.common.excel.GameConfig
import com.mikai233.common.excel.GameConfigManager
import com.mikai233.common.excel.GameConfigManagerSerde
import com.mikai233.common.excel.GameConfigs
import com.mikai233.common.excel.V
import io.github.mikai233.asteria.core.AsteriaModule
import io.github.mikai233.asteria.core.ModuleContext
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.await
import org.apache.curator.framework.recipes.cache.CuratorCache
import org.apache.curator.framework.recipes.cache.CuratorCacheListener
import org.apache.curator.x.async.AsyncCuratorFramework
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

class EntitySerializationModule : AsteriaModule {
    override val name: String = "entity-serialization"

    override suspend fun start(context: ModuleContext) {
        thread { EntityKryoPool }
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

class MongoDbModule(
    private val zookeeper: () -> AsyncCuratorFramework,
) : AsteriaModule {
    override val name: String = "game-mongodb"

    override suspend fun install(context: ModuleContext) {
        context.services.register(MongoDB::class, MongoDB(zookeeper()))
    }
}

class GameConfigService(
    zookeeper: AsyncCuratorFramework,
) {
    private val gameWorldMetaCache = ConfigCache(zookeeper, GAME_WORLDS, GameWorldMeta::class)

    val gameWorldMeta: GameWorldMeta get() = gameWorldMetaCache.config

    val gameWorldConfigCache = ConfigChildrenCache(zookeeper, GAME_WORLDS, GameWorldConfig::class)

    @Volatile
    lateinit var gameConfigManager: GameConfigManager
        private set

    @Volatile
    lateinit var gameConfigManagerHashcode: HashCode
        private set

    internal fun updateGameConfig(bytes: ByteArray) {
        gameConfigManager = GameConfigManagerSerde.deserialize(bytes)
        gameConfigManagerHashcode = Hashing.murmur3_128(233).hashBytes(bytes)
    }

    inline fun <reified T : V> getConfig(): T {
        return gameConfigManager.get<T>()
    }

    inline fun <reified T : GameConfigs<K, C>, C : GameConfig<K>, K : Any> getConfigById(id: K): C {
        return gameConfigManager.getById<T, _, _>(id)
    }
}

class GameConfigModule(
    private val zookeeper: () -> AsyncCuratorFramework,
) : AsteriaModule {
    override val name: String = "game-config"

    private val logger = LoggerFactory.getLogger(GameConfigModule::class.java)
    private var cache: CuratorCache? = null

    override suspend fun install(context: ModuleContext) {
        val client = zookeeper()
        val service = GameConfigService(client)
        service.updateGameConfig(client.data.forPath(GAME_CONFIG).await())
        context.services.register(GameConfigService::class, service)

        cache = CuratorCache.build(client.unwrap(), GAME_CONFIG, CuratorCache.Options.SINGLE_NODE_CACHE).also { cache ->
            cache.listenable().addListener { type, oldData, data ->
                when (type) {
                    CuratorCacheListener.Type.NODE_CREATED,
                    CuratorCacheListener.Type.NODE_CHANGED,
                    -> {
                        service.updateGameConfig(data.data)
                        logger.info(
                            "{} updated, version: {}",
                            GameConfigManager::class.simpleName,
                            service.gameConfigManager.version,
                        )
                        context.services.find(ActorSystem::class)?.eventStream?.publish(GameConfigUpdateEvent)
                    }

                    CuratorCacheListener.Type.NODE_DELETED -> {
                        logger.warn("Node {} deleted:{}", GameConfigManager::class.simpleName, oldData.path)
                    }

                    null -> Unit
                }
            }
            cache.start()
        }
    }

    override suspend fun stop(context: ModuleContext) {
        cache?.close()
        cache = null
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
