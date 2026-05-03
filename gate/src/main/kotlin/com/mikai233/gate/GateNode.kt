package com.mikai233.gate

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.common.broadcast.PlayerBroadcastEnvelope
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.message.ActorMessageDispatcher
import com.mikai233.common.message.ClientProtobuf
import com.mikai233.common.message.StopChannel
import com.mikai233.common.message.ChannelExpired
import com.mikai233.common.message.ChannelAuthorized
import com.mikai233.common.rpc.GameRpcProtocolDefinition
import com.mikai233.gate.handler.message.broadcast.PlayerBroadcastEnvelopeHandler
import com.mikai233.gate.handler.message.channel.SubscribeTopicHandler
import com.mikai233.gate.handler.message.channel.UnsubscribeTopicHandler
import com.mikai233.gate.handler.protocol.system.PingReqHandler
import com.mikai233.protocol.ProtoRpc.BroadcastEnvelope
import com.mikai233.protocol.ProtoRpc.SubscribeTopicReq
import com.mikai233.protocol.ProtoRpc.UnsubscribeTopicReq
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.mikai233.asteria.core.AsteriaApplicationBuilder
import io.github.mikai233.asteria.cluster.pekko.extractor
import com.mikai233.protocol.ProtoSystem.PingReq
import org.apache.pekko.actor.ActorRef
import java.net.InetSocketAddress

class GateNode(
    addr: InetSocketAddress,
    name: String,
    nodeId: String = "gate-${addr.port}",
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : GameNodeRuntime(addr, listOf(Role.Gate), name, nodeId, config, zookeeperConnectString, sameJvm) {
    val playerSharding: ActorRef
        get() = entityShard(ShardEntityType.PlayerActor)

    val worldSharding: ActorRef
        get() = entityShard(ShardEntityType.WorldActor)

    private val pingReqHandler = PingReqHandler()
    private val playerBroadcastEnvelopeHandler = PlayerBroadcastEnvelopeHandler()
    private val subscribeTopicHandler = SubscribeTopicHandler()
    private val unsubscribeTopicHandler = UnsubscribeTopicHandler()

    val protobufDispatcher = ActorMessageDispatcher<ChannelActor, GeneratedMessage>(this).apply {
        register(PingReq::class, pingReqHandler)
        register(PlayerBroadcastEnvelope::class, playerBroadcastEnvelopeHandler)
        register(SubscribeTopicReq::class, subscribeTopicHandler)
        register(UnsubscribeTopicReq::class, unsubscribeTopicHandler)
    }

    val protocolCodec = GateProtocolCodec()

    val gatewayRouter: GateGatewayRouter by lazy { GateGatewayRouter(this) }

    override fun modulesAfterCluster() = listOf(GateGatewayTransportModule(this))

    override fun configureRuntime(builder: AsteriaApplicationBuilder) {
        builder.apply {
            entity<Long>(ShardEntityType.PlayerActor.name) {
                role(Role.Player.name)
                shardCount = PLAYER_SHARD_NUM
                extractor(GameRpcProtocolDefinition.playerShardExtractor)
            }
            entity<Long>(ShardEntityType.WorldActor.name) {
                role(Role.World.name)
                shardCount = WORLD_SHARD_NUM
                extractor(GameRpcProtocolDefinition.worldShardExtractor)
            }
        }
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
