package com.mikai233.common.core

import com.google.common.io.Resources
import com.mikai233.common.broadcast.PlayerBroadcastEventBus
import com.mikai233.common.config.GameWorldConfig
import com.mikai233.common.config.WORKER_IDS
import com.mikai233.common.config.luban.GameTables
import com.mikai233.common.db.MongoDB
import com.mikai233.common.extension.asyncZookeeperClient
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.realmlabs.asteria.cluster.pekko.EntityShardRegistry
import io.github.realmlabs.asteria.cluster.pekko.SingletonActorRegistry
import io.github.realmlabs.asteria.cluster.pekko.addSuspendTask
import io.github.realmlabs.asteria.config.ConfigService
import io.github.realmlabs.asteria.config.center.zookeeper.ZookeeperConfigCenterModule
import io.github.realmlabs.asteria.config.requireComponent
import io.github.realmlabs.asteria.core.*
import io.github.realmlabs.asteria.id.WorkerIdModule
import io.github.realmlabs.asteria.id.WorkerIdModuleOptions
import io.github.realmlabs.asteria.id.WorkerIdOwner
import io.github.realmlabs.asteria.id.zookeeper.ZookeeperWorkerIdRepository
import io.github.realmlabs.asteria.patch.PatchableServiceRegistry
import io.github.realmlabs.asteria.script.engine.groovy.GroovyScriptEngine
import io.github.realmlabs.asteria.script.engine.jar.JarScriptEngine
import io.github.realmlabs.asteria.script.pekko.ScriptModule
import io.github.realmlabs.asteria.starter.clusterGameApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import org.apache.curator.x.async.AsyncCuratorFramework
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.CoordinatedShutdown
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.time.Duration.Companion.milliseconds

object GameRoles {
    const val Player = "Player"
    const val Gate = "Gate"
    const val World = "World"
    const val Global = "Global"
    const val Gm = "Gm"

    val all: List<String> = listOf(Player, Gate, World, Global, Gm)
}

object GameEntityKinds {
    const val PlayerActor = "PlayerActor"
    const val WorldActor = "WorldActor"

    val all: List<String> = listOf(PlayerActor, WorldActor)
}

object GameSingletons {
    const val Worker = "worker"
    const val Monitor = "monitor"

    val all: List<String> = listOf(Worker, Monitor)
}

interface LaunchableNode : NodeRuntime {
    suspend fun launch()
}

val NodeRuntime.system: ActorSystem
    get() = services.get(ActorSystem::class)

suspend fun NodeRuntime.awaitTermination() {
    system.getWhenTerminated().await()
}

val NodeRuntime.coroutineScope: CoroutineScope
    get() = services.get(CoroutineScope::class)

val NodeRuntime.mongoDB: MongoDB
    get() = services.get(MongoDB::class)

val NodeRuntime.gameWorldIds: Set<Long>
    get() = gameWorldConfigService.worldIds

val NodeRuntime.gameWorldConfigs: Map<Long, GameWorldConfig>
    get() = gameWorldConfigService.worldsById

val NodeRuntime.gameTables: GameTables
    get() = services.get(ConfigService::class).current().requireComponent()

val NodeRuntime.broadcastRouter: ActorRef
    get() = services.get(PlayerBroadcastRuntime::class).router

val NodeRuntime.playerBroadcastEventBus: PlayerBroadcastEventBus
    get() = services.get(PlayerBroadcastEventBus::class)

val NodeRuntime.patchableServices: PatchableServiceRegistry
    get() = services.get(PatchableServiceRegistry::class)

fun NodeRuntime.entityShard(kind: String): ActorRef {
    return services.get(EntityShardRegistry::class)[EntityKind(kind)]
}

fun NodeRuntime.singletonActor(name: String): ActorRef {
    return services.get(SingletonActorRegistry::class)[SingletonName(name)]
}

fun versionText(): String = Resources.getResource("version").readText()

private val NodeRuntime.gameWorldConfigService: GameWorldConfigService
    get() = services.get(GameWorldConfigService::class)

class ClusterNodeBootstrap(
    private val runtime: NodeRuntime,
    private val addr: InetSocketAddress,
    private val nodeId: String,
    private val config: Config,
    private val zookeeperConnectString: String,
    private val sameJvm: Boolean = false,
) {
    private val logger = LoggerFactory.getLogger(runtime::class.java)

    private val zookeeperClient: AsyncCuratorFramework by lazy {
        asyncZookeeperClient(zookeeperConnectString)
    }

    val zookeeper: AsyncCuratorFramework
        get() = runtime.services.find(AsyncCuratorFramework::class) ?: zookeeperClient

    suspend fun launch(
        beforeClusterModules: List<AsteriaModule> = emptyList(),
        afterClusterModules: List<AsteriaModule> = emptyList(),
        onStateChange: (NodeState) -> Unit,
        configure: AsteriaApplicationBuilder.() -> Unit,
    ) {
        val application = clusterGameApplication(nodeId = nodeId, pekkoConfig = runtimeConfig()) {
            name = runtime.name
            commonModules().forEach(::install)
            beforeClusterModules.forEach(::install)
            configure()
            install(PekkoCoroutineScopeModule())
            install(
                ScriptModule {
                    engine(GroovyScriptEngine())
                    engine(JarScriptEngine())
                    allowNodeScripts = true
                    allowActorScripts = true
                },
            )
            afterClusterModules.forEach(::install)
        }
        val lifecycle = application.bind(runtime) { newState ->
            val previousState = runtime.state
            onStateChange(newState)
            logger.info("{} state change from:{} to:{}", runtime::class.simpleName, previousState, newState)
        }
        lifecycle.launch()
        addCoordinatedShutdownTasks(onStateChange)
    }

    fun workerIdModule(): AsteriaModule {
        return WorkerIdModule(
            repository = ZookeeperWorkerIdRepository(zookeeper, WORKER_IDS),
            options = WorkerIdModuleOptions(
                owner = { WorkerIdOwner(addr.toString()) },
            ),
        )
    }

    private fun commonModules(): List<AsteriaModule> {
        return listOf(
            ZookeeperConfigCenterModule {
                client(zookeeper)
            },
            PrometheusMetricsModule(addr.port + 1000),
            MongoDbModule(),
            GameWorldConfigModule(),
            GameConfigModule(),
            PlayerBroadcastModule(),
        )
    }

    private fun runtimeConfig(): Config {
        return if (sameJvm) {
            ConfigFactory.parseMap(
                mapOf("pekko.cluster.jmx.multi-mbeans-in-same-jvm" to "on"),
            ).withFallback(config)
        } else {
            config
        }
    }

    private fun addCoordinatedShutdownTasks(onStateChange: (NodeState) -> Unit) {
        with(CoordinatedShutdown.get(runtime.system)) {
            addSuspendTask(
                CoordinatedShutdown.PhaseClusterLeave(),
                "leave_delay",
            ) {
                delay(Random.nextLong(1000L..5000L).milliseconds)
            }
            addSuspendTask(
                CoordinatedShutdown.PhaseBeforeServiceUnbind(),
                "change_state_stopping",
            ) {
                    onStateChange(NodeState.Stopping)
            }
            addSuspendTask(
                CoordinatedShutdown.PhaseActorSystemTerminate(),
                "change_state_stopped",
            ) {
                    onStateChange(NodeState.Stopped)
            }
        }
    }
}
