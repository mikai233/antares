package com.mikai233.player

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.common.config.ConfigChangeDispatcher
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.event.PlayerCreateEvent
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.common.message.player.HandoffPlayer
import com.mikai233.common.rpc.GameRpcProtocolDefinition
import com.mikai233.player.config.PlayerActivityConfigChangeHandler
import com.mikai233.player.handler.event.ConfigChangedEventHandler
import com.mikai233.player.handler.event.PlayerCreateEventHandler
import com.mikai233.player.handler.event.PlayerLoginEventHandler
import com.mikai233.player.handler.gm.TestGmHandler
import com.mikai233.player.handler.message.player.PlayerChannelClosedReqHandler
import com.mikai233.player.handler.message.player.PlayerCreateReqHandler
import com.mikai233.player.handler.message.player.PlayerLoginReqHandler
import com.mikai233.player.handler.protocol.system.GmReqHandler
import com.mikai233.player.handler.protocol.test.TestReqHandler
import com.mikai233.protocol.ProtoRpc.PlayerChannelClosedReq
import com.mikai233.protocol.ProtoRpc.PlayerCreateReq
import com.mikai233.protocol.ProtoRpc.PlayerLoginReq
import com.mikai233.protocol.ProtoSystem.GmReq
import com.mikai233.protocol.ProtoTest.TestReq
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.realmlabs.asteria.core.NodeState
import io.github.realmlabs.asteria.core.RoleKey
import io.github.realmlabs.asteria.core.ServiceRegistry
import io.github.realmlabs.asteria.cluster.pekko.actor
import io.github.realmlabs.asteria.cluster.pekko.allocationStrategy
import io.github.realmlabs.asteria.cluster.pekko.extractor
import io.github.realmlabs.asteria.config.ConfigChangedEvent
import io.github.realmlabs.asteria.id.IdGenerator
import io.github.realmlabs.asteria.message.MessageDispatcher
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

    private val configChangedEventHandler = ConfigChangedEventHandler()
    private val playerActivityConfigChangeHandler = PlayerActivityConfigChangeHandler()
    private val playerCreateEventHandler = PlayerCreateEventHandler()
    private val playerLoginEventHandler = PlayerLoginEventHandler()
    private val testGmHandler = TestGmHandler()
    private val playerChannelClosedReqHandler = PlayerChannelClosedReqHandler()
    private val playerCreateReqHandler = PlayerCreateReqHandler()
    private val playerLoginReqHandler = PlayerLoginReqHandler()
    private val gmReqHandler = GmReqHandler(testGmHandler)
    private val testReqHandler = TestReqHandler()

    private val protobufHandlers = PlayerMessageHandlerRegistry<GeneratedMessage>().apply {
        register(GmReq::class, gmReqHandler)
        register(TestReq::class, testReqHandler)
        register(PlayerLoginReq::class, playerLoginReqHandler)
        register(PlayerCreateReq::class, playerCreateReqHandler)
        register(PlayerChannelClosedReq::class, playerChannelClosedReqHandler)
    }
    val protobufDispatcher = MessageDispatcher(protobufHandlers)

    val configChangeDispatcher = ConfigChangeDispatcher<PlayerActor>(
        listOf(playerActivityConfigChangeHandler),
    )

    private val internalHandlers = PlayerMessageHandlerRegistry<Any>().apply {
        register(ConfigChangedEvent::class, configChangedEventHandler)
        register(PlayerLoginEvent::class, playerLoginEventHandler)
        register(PlayerCreateEvent::class, playerCreateEventHandler)
    }
    val internalDispatcher = MessageDispatcher(internalHandlers)

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
                extractor(GameRpcProtocolDefinition.playerShardExtractor)
                allocationStrategy(ShardCoordinator.LeastShardAllocationStrategy(1, 3))
                actor { runtime, _ -> PlayerActor.props(runtime as PlayerNode) }
            }
            entity<Long>(GameEntityKinds.WorldActor) {
                role(GameRoles.World)
                shardCount = WORLD_SHARD_NUM
                extractor(GameRpcProtocolDefinition.worldShardExtractor)
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
