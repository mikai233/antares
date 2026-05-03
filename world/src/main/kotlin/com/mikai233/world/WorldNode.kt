package com.mikai233.world

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.common.config.ConfigChangeDispatcher
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.event.WorldActiveEvent
import com.mikai233.common.message.Message
import com.mikai233.common.message.world.HandoffWorld
import com.mikai233.common.rpc.GameRpcProtocolDefinition
import com.mikai233.protocol.ProtoLogin.LoginReq
import com.mikai233.protocol.ProtoRpc.CrossWorldSubscribeTopicReq
import com.mikai233.protocol.ProtoRpc.CrossWorldUnsubscribeTopicReq
import com.mikai233.protocol.ProtoRpc.WorldWakeupReq
import com.mikai233.protocol.ProtoSystem.GmReq
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
import com.mikai233.world.handler.event.ConfigChangedEventHandler
import com.mikai233.world.handler.event.WorldActiveEventHandler
import com.mikai233.world.handler.gm.TestBroadcastHandler
import com.mikai233.world.handler.message.world.PlayerLoginHandler
import com.mikai233.world.handler.message.world.SubscribeTopicCrossWorldHandler
import com.mikai233.world.handler.message.world.UnsubscribeTopicCrossWorldHandler
import com.mikai233.world.handler.message.world.WakeupWorldReqHandler
import com.mikai233.world.handler.protocol.system.GmReqHandler
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
    private val worldActiveEventHandler = WorldActiveEventHandler()
    private val testBroadcastHandler = TestBroadcastHandler()
    private val playerLoginHandler = PlayerLoginHandler()
    private val subscribeTopicCrossWorldHandler = SubscribeTopicCrossWorldHandler()
    private val unsubscribeTopicCrossWorldHandler = UnsubscribeTopicCrossWorldHandler()
    private val wakeupWorldReqHandler = WakeupWorldReqHandler()
    private val gmReqHandler = GmReqHandler(testBroadcastHandler)

    private val protobufHandlers = WorldMessageHandlerRegistry<GeneratedMessage>().apply {
        register(GmReq::class, gmReqHandler)
        register(LoginReq::class, playerLoginHandler)
        register(WorldWakeupReq::class, wakeupWorldReqHandler)
        register(CrossWorldSubscribeTopicReq::class, subscribeTopicCrossWorldHandler)
        register(CrossWorldUnsubscribeTopicReq::class, unsubscribeTopicCrossWorldHandler)
    }
    val protobufDispatcher = MessageDispatcher(protobufHandlers)

    val configChangeDispatcher = ConfigChangeDispatcher<WorldActor>()

    private val internalHandlers = WorldMessageHandlerRegistry<Any>().apply {
        register(WorldActiveEvent::class, worldActiveEventHandler)
        register(ConfigChangedEvent::class, configChangedEventHandler)
    }
    val internalDispatcher = MessageDispatcher(internalHandlers)

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
                extractor(GameRpcProtocolDefinition.playerShardExtractor)
            }
            entity<Long>(GameEntityKinds.WorldActor) {
                role(GameRoles.World)
                shardCount = WORLD_SHARD_NUM
                handoffMessage = HandoffWorld
                extractor(GameRpcProtocolDefinition.worldShardExtractor)
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
    WorldNode(addr, cli.name, cli.nodeId ?: "world-${cli.port}", config, cli.zookeeper).launch()
}
