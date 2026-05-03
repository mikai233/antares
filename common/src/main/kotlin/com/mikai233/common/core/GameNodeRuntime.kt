package com.mikai233.common.core

import com.google.common.io.Resources
import com.mikai233.common.broadcast.PlayerBroadcastEventBus
import com.mikai233.common.config.*
import com.mikai233.common.config.luban.GameTables
import com.mikai233.common.db.MongoDB
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.realmlabs.asteria.config.center.zookeeper.ZookeeperConfigCenterModule
import io.github.realmlabs.asteria.config.requireComponent
import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.AsteriaApplicationBuilder
import io.github.realmlabs.asteria.core.EntityKind
import io.github.realmlabs.asteria.core.NodeState
import io.github.realmlabs.asteria.core.NodeRuntime
import io.github.realmlabs.asteria.core.RoleKey
import io.github.realmlabs.asteria.core.ServiceRegistry
import io.github.realmlabs.asteria.core.SingletonName
import io.github.realmlabs.asteria.core.gameApplication
import io.github.realmlabs.asteria.id.WorkerIdModule
import io.github.realmlabs.asteria.id.WorkerIdModuleOptions
import io.github.realmlabs.asteria.id.WorkerIdOwner
import io.github.realmlabs.asteria.id.zookeeper.ZookeeperWorkerIdRepository
import io.github.realmlabs.asteria.cluster.pekko.EntityShardRegistry
import io.github.realmlabs.asteria.cluster.pekko.SingletonActorRegistry
import io.github.realmlabs.asteria.script.engine.groovy.GroovyScriptEngine
import io.github.realmlabs.asteria.script.engine.jar.JarScriptEngine
import io.github.realmlabs.asteria.script.pekko.ScriptModule
import io.github.realmlabs.asteria.starter.clusterGameApplication
import kotlinx.coroutines.*
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.x.async.AsyncCuratorFramework
import org.apache.pekko.Done
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.CoordinatedShutdown
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Supplier
import kotlin.random.Random
import kotlin.random.nextLong

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/9
 * @param addr 节点地址
 * @param roles 节点角色
 * @param name 节点名称
 * @param config 节点配置
 * @param zookeeperConnectString zookeeper连接字符串
 */
open class GameNodeRuntime(
    val addr: InetSocketAddress,
    val nodeRoles: List<Role>,
    override val name: String,
    val nodeId: String,
    val config: Config,
    zookeeperConnectString: String,
    private val sameJvm: Boolean = false,
) : NodeRuntime {
    val logger: Logger = LoggerFactory.getLogger(javaClass)

    override val roles: Set<RoleKey>
        get() = nodeRoles.mapTo(linkedSetOf()) { RoleKey(it.name) }

    override val services: ServiceRegistry = ServiceRegistry()

    val system: ActorSystem
        get() = services.get(ActorSystem::class)

    val coroutineScope: CoroutineScope
        get() = services.get(CoroutineScope::class)

    private val zookeeperClient: AsyncCuratorFramework by lazy {
        val client = CuratorFrameworkFactory.newClient(
            zookeeperConnectString,
            ExponentialBackoffRetry(2000, 10, 60000),
        )
        client.start()
        AsyncCuratorFramework.wrap(client)
    }

    val zookeeper: AsyncCuratorFramework
        get() = services.find(AsyncCuratorFramework::class) ?: zookeeperClient

    val mongoDB: MongoDB
        get() = services.get(MongoDB::class)

    val gameWorldIds: Set<Long>
        get() = gameWorldConfigService.worldIds

    val gameWorldConfigs: Map<Long, GameWorldConfig>
        get() = gameWorldConfigService.worldsById

    val gameConfigVersion: String
        get() = services.get(io.github.realmlabs.asteria.config.ConfigService::class).current().revision.version

    val gameTables: GameTables
        get() = services.get(io.github.realmlabs.asteria.config.ConfigService::class).current().requireComponent()

    val broadcastRouter: ActorRef
        get() = services.get(PlayerBroadcastRuntime::class).router

    val playerBroadcastEventBus: PlayerBroadcastEventBus
        get() = services.get(PlayerBroadcastEventBus::class)

    @PublishedApi
    internal val gameWorldConfigService: GameWorldConfigService
        get() = services.get(GameWorldConfigService::class)

    @Volatile
    final override var state: NodeState = NodeState.Unstarted
        private set

    private fun updateState(newState: NodeState) {
        val previousState = state
        state = newState
        logger.info("{} state change from:{} to:{}", this::class.simpleName, previousState, newState)
    }

    open suspend fun launch() = start()

    protected open suspend fun start() {
        startSystem()
    }

    protected open suspend fun startSystem() {
        val application = clusterGameApplication(nodeId = nodeId, pekkoConfig = runtimeConfig()) {
            name = this@GameNodeRuntime.name
            commonModulesBeforeCluster().forEach(::install)
            modulesBeforeCluster().forEach(::install)
            configureRuntime(this)
            install(PekkoCoroutineScopeModule())
            install(
                ScriptModule {
                    engine(GroovyScriptEngine())
                    engine(JarScriptEngine())
                    allowNodeScripts = true
                    allowActorScripts = true
                },
            )
            modulesAfterCluster().forEach(::install)
        }
        val lifecycle = application.bind(this) { newState ->
            updateState(newState)
        }
        lifecycle.launch()
        addCoordinatedShutdownTasks()
    }

    private fun addCoordinatedShutdownTasks() {
        with(CoordinatedShutdown.get(system)) {
            addTask(
                CoordinatedShutdown.PhaseClusterLeave(), "leave_delay",
                taskSupplier {
                    delay(Random.nextLong(1000L..5000L))
                },
            )
            addTask(
                CoordinatedShutdown.PhaseBeforeServiceUnbind(), "change_state_stopping",
                taskSupplier {
                    updateState(NodeState.Stopping)
                },
            )
            addTask(
                CoordinatedShutdown.PhaseActorSystemTerminate(), "change_state_stopped",
                taskSupplier {
                    updateState(NodeState.Stopped)
                },
            )
        }
    }

    protected open fun taskSupplier(task: suspend () -> Unit): Supplier<CompletionStage<Done>> {
        return Supplier {
            CompletableFuture.supplyAsync {
                runBlocking { task.invoke() }
                Done.done()
            }
        }
    }

    protected open fun configureRuntime(builder: AsteriaApplicationBuilder) {
        builder.apply {
            nodeRoles.forEach { role(it.name) }
        }
    }

    protected open fun modulesBeforeCluster(): List<AsteriaModule> = emptyList()

    protected open fun modulesAfterCluster(): List<AsteriaModule> = emptyList()

    private fun commonModulesBeforeCluster(): List<AsteriaModule> {
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

    protected fun workerIdRuntimeModule(): AsteriaModule {
        return WorkerIdModule(
            repository = ZookeeperWorkerIdRepository(zookeeper, WORKER_IDS),
            options = WorkerIdModuleOptions(
                owner = { WorkerIdOwner(addr.toString()) },
            ),
        )
    }

    protected fun entityShard(type: ShardEntityType): ActorRef {
        return services.get(EntityShardRegistry::class)[EntityKind(type.name)]
    }

    protected fun singletonActor(singleton: Singleton): ActorRef {
        return services.get(SingletonActorRegistry::class)[SingletonName(singleton.actorName)]
    }

    fun version() = Resources.getResource("version").readText()
}
