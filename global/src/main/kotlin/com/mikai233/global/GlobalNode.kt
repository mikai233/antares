package com.mikai233.global

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.conf.RuntimeEnv
import com.mikai233.common.config.SYSTEM_NAME
import com.mikai233.common.rpc.DefaultRpcEntityIdResolver
import com.mikai233.common.rpc.GameRpcProtocol
import com.mikai233.common.rpc.RpcEntityIdResolver
import com.mikai233.common.runtime.*
import com.mikai233.global.actor.ShutdownCoordinatorActor
import com.mikai233.global.actor.WorkerActor
import com.mikai233.global.message.HandoffShutdownCoordinator
import com.mikai233.global.message.HandoffWorker
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.realmlabs.asteria.cluster.pekko.actor
import io.github.realmlabs.asteria.cluster.pekko.extractor
import io.github.realmlabs.asteria.core.NodeState
import io.github.realmlabs.asteria.core.RoleKey
import io.github.realmlabs.asteria.core.ServiceRegistry
import io.github.realmlabs.asteria.patch.PatchableServiceRegistry
import org.apache.pekko.actor.ActorRef
import java.net.InetSocketAddress

class GlobalNode(
    val addr: InetSocketAddress,
    override val name: String,
    val nodeId: String = "global-${addr.port}",
    val config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
    val runtimeEnv: RuntimeEnv = RuntimeEnv.fromSystem(),
) : LaunchableNode {
    override val roles: Set<RoleKey> = setOf(RoleKey(GameRoles.Global))
    override val services: ServiceRegistry = ServiceRegistry()

    @Volatile
    private var currentState: NodeState = NodeState.Unstarted

    override val state: NodeState
        get() = currentState

    private val clusterNode = ClusterNodeBootstrap(this, addr, nodeId, config, zookeeperConnectString, sameJvm)

    val playerSharding: ActorRef
        get() = entityShard(GameEntityKinds.PlayerActor)

    val worldSharding: ActorRef
        get() = entityShard(GameEntityKinds.WorldActor)

    val workerActor: ActorRef
        get() = singletonActor(GameSingletons.Worker)

    val shutdownCoordinator: ActorRef
        get() = singletonActor(GameSingletons.ShutdownCoordinator)

    init {
        val patchableServices = PatchableServiceRegistry().apply {
            register(RpcEntityIdResolver::class, DefaultRpcEntityIdResolver(GameRpcProtocol.protocol))
        }
        services.register(
            GamePatchBindings::class,
            GamePatchBindings(services = patchableServices),
        )
        services.register(PatchableServiceRegistry::class, patchableServices)
        services.register(StartupLikeReloadPlan::class, GlobalGameTimeReloadPlan(this))
    }

    override suspend fun launch() {
        clusterNode.launch(onStateChange = ::updateState) {
            role(GameRoles.Global)
            entity<Long>(GameEntityKinds.PlayerActor) {
                role(GameRoles.Player)
                shardCount = PLAYER_SHARD_NUM
                extractor(GameRpcProtocol.playerShardExtractor(this@GlobalNode))
            }
            entity<Long>(GameEntityKinds.WorldActor) {
                role(GameRoles.World)
                shardCount = WORLD_SHARD_NUM
                extractor(GameRpcProtocol.worldShardExtractor(this@GlobalNode))
            }
            singleton(GameSingletons.Worker) {
                role(GameRoles.Global)
                handoffMessage = HandoffWorker
                actor { runtime, _ ->
                    SingletonChildSupervisorActor.props(
                        childName = "worker",
                        childProps = WorkerActor.props(runtime as GlobalNode),
                        childStopMessage = HandoffWorker,
                        handoffMessageType = HandoffWorker::class.java,
                    )
                }
            }
            singleton(GameSingletons.ShutdownCoordinator) {
                role(GameRoles.Global)
                handoffMessage = HandoffShutdownCoordinator
                actor { runtime, _ -> ShutdownCoordinatorActor.props(runtime as GlobalNode) }
            }
        }
    }

    private fun updateState(newState: NodeState) {
        currentState = newState
    }

}

private class Cli(runtimeEnv: RuntimeEnv) {
    @Parameter(names = ["-h", "--host"], description = "host")
    var host: String = runtimeEnv.machineIp

    @Parameter(names = ["-p", "--port"], description = "port")
    var port: Int = 2335

    @Parameter(names = ["-c", "--conf"], description = "conf")
    var conf: String = "global.conf"

    @Parameter(names = ["-z", "--zookeeper"], description = "zookeeper")
    var zookeeper: String = runtimeEnv.zookeeperConnect

    @Parameter(names = ["-n", "--name"], description = "system name")
    var name: String = SYSTEM_NAME

    @Parameter(names = ["-i", "--node-id"], description = "runtime node id")
    var nodeId: String? = null
}

suspend fun main(args: Array<String>) {
    val runtimeEnv = RuntimeEnv.fromSystem()
    val cli = Cli(runtimeEnv)
    @Suppress("SpreadOperator")
    JCommander.newBuilder()
        .addObject(cli)
        .build()
        .parse(*args)
    val addr = InetSocketAddress(cli.host, cli.port)
    val config = ConfigFactory.load(cli.conf)
    GlobalNode(
        addr,
        cli.name,
        cli.nodeId ?: "global-${cli.port}",
        config,
        cli.zookeeper,
        runtimeEnv = runtimeEnv,
    ).also {
        it.launch()
        it.awaitTermination()
    }
}
