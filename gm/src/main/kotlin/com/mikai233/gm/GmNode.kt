package com.mikai233.gm

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.conf.RuntimeEnv
import com.mikai233.common.config.SYSTEM_NAME
import com.mikai233.common.rpc.GameRpcProtocol
import com.mikai233.common.runtime.*
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.realmlabs.asteria.cluster.pekko.PekkoSingletonStartup
import io.github.realmlabs.asteria.cluster.pekko.extractor
import io.github.realmlabs.asteria.cluster.pekko.singletonStartup
import io.github.realmlabs.asteria.core.NodeState
import io.github.realmlabs.asteria.core.RoleKey
import io.github.realmlabs.asteria.core.ServiceRegistry
import org.apache.pekko.actor.ActorRef
import java.net.InetSocketAddress

class GmNode(
    val addr: InetSocketAddress,
    override val name: String,
    val nodeId: String = "gm-${addr.port}",
    val config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
    val runtimeEnv: RuntimeEnv = RuntimeEnv.fromSystem(),
) : LaunchableNode {
    override val roles: Set<RoleKey> = setOf(RoleKey(GameRoles.Gm))
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

    val workerSingletonProxy: ActorRef
        get() = singletonActor(GameSingletons.Worker)

    val shutdownCoordinator: ActorRef
        get() = singletonActor(GameSingletons.ShutdownCoordinator)

    override suspend fun launch() {
        clusterNode.launch(
            afterClusterModules = listOf(GmRuntimeModule(this)),
            onStateChange = ::updateState,
        ) {
            role(GameRoles.Gm)
            entity<Long>(GameEntityKinds.PlayerActor) {
                role(GameRoles.Player)
                shardCount = PLAYER_SHARD_NUM
                extractor(GameRpcProtocol.playerShardExtractor(this@GmNode))
            }
            entity<Long>(GameEntityKinds.WorldActor) {
                role(GameRoles.World)
                shardCount = WORLD_SHARD_NUM
                extractor(GameRpcProtocol.worldShardExtractor(this@GmNode))
            }
            singleton(GameSingletons.Worker) {
                role(GameRoles.Global)
                singletonStartup(PekkoSingletonStartup.Proxy)
            }
            singleton(GameSingletons.ShutdownCoordinator) {
                role(GameRoles.Global)
                singletonStartup(PekkoSingletonStartup.Proxy)
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
    var port: Int = 2336

    @Parameter(names = ["-c", "--conf"], description = "conf")
    var conf: String = "gm.conf"

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
    GmNode(addr, cli.name, cli.nodeId ?: "gm-${cli.port}", config, cli.zookeeper, runtimeEnv = runtimeEnv).also {
        it.launch()
        it.awaitTermination()
    }
}
