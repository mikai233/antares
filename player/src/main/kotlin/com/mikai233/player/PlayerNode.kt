package com.mikai233.player

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.common.config.ConfigChangeDispatcher
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.event.PlayerCreateEvent
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.common.message.player.HandoffPlayer
import com.mikai233.common.message.catalog.MessageCatalog
import com.mikai233.common.rpc.GameRpcProtocol
import com.mikai233.player.generated.GeneratedPlayerMessageCatalog
import com.mikai233.player.generated.GeneratedPlayerNodeDispatchers
import com.mikai233.player.config.PlayerActivityConfigChangeHandler
import com.mikai233.player.service.LoginService
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.realmlabs.asteria.core.NodeState
import io.github.realmlabs.asteria.core.RoleKey
import io.github.realmlabs.asteria.core.ServiceRegistry
import io.github.realmlabs.asteria.cluster.pekko.actor
import io.github.realmlabs.asteria.cluster.pekko.allocationStrategy
import io.github.realmlabs.asteria.cluster.pekko.extractor
import io.github.realmlabs.asteria.id.IdGenerator
import io.github.realmlabs.asteria.patch.PatchableServiceRegistry
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.cluster.sharding.ShardCoordinator
import java.net.InetSocketAddress

class PlayerNode(
    val addr: InetSocketAddress,
    override val name: String,
    val nodeId: String = "player-${addr.port}",
    val config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : LaunchableNode {
    override val roles: Set<RoleKey> = setOf(RoleKey(GameRoles.Player))
    override val services: ServiceRegistry = ServiceRegistry()

    val loginService: LoginService
        get() = patchableServices.require(LoginService::class)

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

    private val playerActivityConfigChangeHandler = PlayerActivityConfigChangeHandler()
    val protobufDispatcher = GeneratedPlayerNodeDispatchers.PROTOBUF

    val configChangeDispatcher = ConfigChangeDispatcher<PlayerActor>(
        listOf(playerActivityConfigChangeHandler),
    )

    val messageCatalog: MessageCatalog
        get() = GeneratedPlayerMessageCatalog
    val internalDispatcher = GeneratedPlayerNodeDispatchers.INTERNAL

    init {
        val patchableServices = PatchableServiceRegistry().apply {
            register(LoginService::class, LoginService())
        }
        services.register(PatchableServiceRegistry::class, patchableServices)
    }

    override suspend fun launch() {
        clusterNode.launch(
            beforeClusterModules = listOf(clusterNode.workerIdModule()),
            onStateChange = ::updateState,
        ) {
            role(GameRoles.Player)
            entity<Long>(GameEntityKinds.PlayerActor) {
                role(GameRoles.Player)
                shardCount = PLAYER_SHARD_NUM
                handoffMessage = HandoffPlayer
                extractor(GameRpcProtocol.playerShardExtractor)
                allocationStrategy(ShardCoordinator.LeastShardAllocationStrategy(1, 3))
                actor { runtime, _ -> PlayerActor.props(runtime as PlayerNode) }
            }
            entity<Long>(GameEntityKinds.WorldActor) {
                role(GameRoles.World)
                shardCount = WORLD_SHARD_NUM
                extractor(GameRpcProtocol.worldShardExtractor)
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
    var port: Int = 2333

    @Parameter(names = ["-c", "--conf"], description = "conf")
    var conf: String = "player.conf"

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
    PlayerNode(addr, cli.name, cli.nodeId ?: "player-${cli.port}", config, cli.zookeeper).launch()
}
