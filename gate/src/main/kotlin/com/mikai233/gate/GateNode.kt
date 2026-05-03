package com.mikai233.gate

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.common.broadcast.PlayerBroadcastEnvelope
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.message.ClientProtobuf
import com.mikai233.common.message.StopChannel
import com.mikai233.common.message.ChannelExpired
import com.mikai233.common.message.ChannelAuthorized
import com.mikai233.common.message.catalog.MessageCatalog
import com.mikai233.common.rpc.GameRpcProtocol
import com.mikai233.gate.generated.GeneratedGateMessageCatalog
import com.mikai233.gate.generated.GeneratedGateNodeDispatchers
import com.mikai233.protocol.ProtoRpcBroadcast.BroadcastEnvelope
import com.mikai233.protocol.ProtoRpcWorld.SubscribeTopicReq
import com.mikai233.protocol.ProtoRpcWorld.UnsubscribeTopicReq
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.realmlabs.asteria.core.NodeState
import io.github.realmlabs.asteria.core.RoleKey
import io.github.realmlabs.asteria.core.ServiceRegistry
import io.github.realmlabs.asteria.cluster.pekko.extractor
import com.mikai233.protocol.ProtoSystem.PingReq
import org.apache.pekko.actor.ActorRef
import java.net.InetSocketAddress

class GateNode(
    val addr: InetSocketAddress,
    override val name: String,
    val nodeId: String = "gate-${addr.port}",
    val config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : LaunchableNode {
    override val roles: Set<RoleKey> = setOf(RoleKey(GameRoles.Gate))
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

    val protobufDispatcher = GeneratedGateNodeDispatchers.PROTOBUF

    val protocolCodec = GateProtocolCodec()

    val gatewayRouter: GateGatewayRouter by lazy { GateGatewayRouter(this) }

    val messageCatalog: MessageCatalog
        get() = GeneratedGateMessageCatalog

    override suspend fun launch() {
        clusterNode.launch(
            afterClusterModules = listOf(GateGatewayTransportModule(this)),
            onStateChange = ::updateState,
        ) {
            role(GameRoles.Gate)
            entity<Long>(GameEntityKinds.PlayerActor) {
                role(GameRoles.Player)
                shardCount = PLAYER_SHARD_NUM
                extractor(GameRpcProtocol.playerShardExtractor)
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
    var port: Int = 2334

    @Parameter(names = ["-c", "--conf"], description = "conf")
    var conf: String = "gate.conf"

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
    GateNode(addr, cli.name, cli.nodeId ?: "gate-${cli.port}", config, cli.zookeeper).launch()
}
