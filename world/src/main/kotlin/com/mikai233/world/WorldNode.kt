package com.mikai233.world

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.config.ConfigChangeDispatcher
import com.mikai233.common.core.*
import com.mikai233.common.message.world.HandoffWorld
import com.mikai233.common.rpc.DefaultRpcEntityIdResolver
import com.mikai233.common.rpc.GameRpcProtocol
import com.mikai233.common.rpc.RpcEntityIdResolver
import com.mikai233.world.generated.GeneratedWorldConfigChangeHandlers
import com.mikai233.world.generated.GeneratedWorldMessageCatalog
import com.mikai233.world.generated.GeneratedWorldNodeDispatchers
import com.mikai233.world.service.WorldService
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.realmlabs.asteria.cluster.pekko.actor
import io.github.realmlabs.asteria.cluster.pekko.allocationStrategy
import io.github.realmlabs.asteria.cluster.pekko.extractor
import io.github.realmlabs.asteria.core.NodeState
import io.github.realmlabs.asteria.core.RoleKey
import io.github.realmlabs.asteria.core.ServiceRegistry
import io.github.realmlabs.asteria.id.IdGenerator
import io.github.realmlabs.asteria.message.MessageCatalog
import io.github.realmlabs.asteria.patch.PatchableServiceRegistry
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.cluster.sharding.ShardCoordinator
import java.net.InetSocketAddress

class WorldNode(
    val addr: InetSocketAddress,
    override val name: String,
    val nodeId: String = "world-${addr.port}",
    val config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : LaunchableNode {
    override val roles: Set<RoleKey> = setOf(RoleKey(GameRoles.World))
    override val services: ServiceRegistry = ServiceRegistry()

    val worldService: WorldService
        get() = patchableServices.require(WorldService::class)

    @Volatile
    private var currentState: NodeState = NodeState.Unstarted

    override val state: NodeState
        get() = currentState

    private val clusterNode = ClusterNodeBootstrap(this, addr, nodeId, config, zookeeperConnectString, sameJvm)

    val playerSharding: ActorRef
        get() = entityShard(GameEntityKinds.PlayerActor)

    val worldSharding: ActorRef
        get() = entityShard(GameEntityKinds.WorldActor)

    val idGenerator: IdGenerator
        get() = services.get(IdGenerator::class)

    val protobufDispatcher = GeneratedWorldNodeDispatchers.PROTOBUF

    val configChangeDispatcher = ConfigChangeDispatcher(
        GeneratedWorldConfigChangeHandlers.ALL,
    )

    val messageCatalog: MessageCatalog
        get() = GeneratedWorldMessageCatalog
    val internalDispatcher = GeneratedWorldNodeDispatchers.INTERNAL

    init {
        val patchableServices = PatchableServiceRegistry().apply {
            register(WorldService::class, WorldService())
            register(RpcEntityIdResolver::class, DefaultRpcEntityIdResolver(GameRpcProtocol.protocol))
        }
        services.register(PatchableServiceRegistry::class, patchableServices)
    }

    override suspend fun launch() {
        clusterNode.launch(
            beforeClusterModules = listOf(clusterNode.workerIdModule()),
            afterClusterModules = listOf(WorldWakerModule(this)),
            onStateChange = ::updateState,
        ) {
            role(GameRoles.World)
            entity<Long>(GameEntityKinds.PlayerActor) {
                role(GameRoles.Player)
                shardCount = PLAYER_SHARD_NUM
                extractor(GameRpcProtocol.playerShardExtractor(this@WorldNode))
            }
            entity<Long>(GameEntityKinds.WorldActor) {
                role(GameRoles.World)
                shardCount = WORLD_SHARD_NUM
                handoffMessage = HandoffWorld
                extractor(GameRpcProtocol.worldShardExtractor(this@WorldNode))
                allocationStrategy(ShardCoordinator.LeastShardAllocationStrategy(1, 3))
                actor { runtime, _ -> WorldActor.props(runtime as WorldNode) }
            }
        }
    }

    private fun updateState(newState: NodeState) {
        currentState = newState
    }

}

private class Cli {
    @Parameter(names = ["-h", "--host"], description = "host")
    var host: String = GlobalEnv.machineIp

    @Parameter(names = ["-p", "--port"], description = "port")
    var port: Int = 2336

    @Parameter(names = ["-c", "--conf"], description = "conf")
    var conf: String = "world.conf"

    @Parameter(names = ["-z", "--zookeeper"], description = "zookeeper")
    var zookeeper: String = GlobalEnv.zkConnect

    @Parameter(names = ["-n", "--name"], description = "system name")
    var name: String = GlobalEnv.SYSTEM_NAME

    @Parameter(names = ["-i", "--node-id"], description = "runtime node id")
    var nodeId: String? = null
}

suspend fun main(args: Array<String>) {
    val cli = Cli()
    @Suppress("SpreadOperator")
    JCommander.newBuilder()
        .addObject(cli)
        .build()
        .parse(*args)
    val addr = InetSocketAddress(cli.host, cli.port)
    val config = ConfigFactory.load(cli.conf)
    WorldNode(addr, cli.name, cli.nodeId ?: "world-${cli.port}", config, cli.zookeeper).also {
        it.launch()
        it.awaitTermination()
    }
}
