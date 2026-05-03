package com.mikai233.gate

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.message.*
import com.mikai233.common.broadcast.PlayerBroadcastEnvelope
import com.mikai233.common.message.channel.SubscribeTopic
import com.mikai233.common.message.channel.UnsubscribeTopic
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.mikai233.asteria.cluster.pekko.EntityShardRegistry
import io.github.mikai233.asteria.cluster.pekko.SingletonActorRegistry
import io.github.mikai233.asteria.core.AsteriaApplicationBuilder
import io.github.mikai233.asteria.cluster.pekko.extractor
import com.mikai233.gate.handler.BroadcastHandler
import com.mikai233.gate.handler.PingHandler
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

    private val pingHandler = PingHandler()
    private val broadcastHandler = BroadcastHandler()

    val protobufDispatcher = ActorMessageDispatcher<ChannelActor, GeneratedMessage>(this).apply {
        register(PingReq::class) { actor, _ -> pingHandler.handlePingReq(actor) }
    }

    val internalDispatcher = ActorMessageDispatcher<ChannelActor, Message>(this).apply {
        register(PlayerBroadcastEnvelope::class, broadcastHandler::handlePlayerBroadcastEnvelope)
        register(SubscribeTopic::class, broadcastHandler::handleSubscribeTopic)
        register(UnsubscribeTopic::class, broadcastHandler::handleUnsubscribeTopic)
    }

    val protocolCodec = GateProtocolCodec()

    val gatewayRouter: GateGatewayRouter by lazy { GateGatewayRouter(this) }

    override fun modulesAfterCluster() = listOf(GateGatewayTransportModule(this))

    override fun configureRuntime(builder: AsteriaApplicationBuilder) {
        builder.apply {
            entity<Long>(ShardEntityType.PlayerActor.name) {
                role(Role.Player.name)
                shardCount = PLAYER_SHARD_NUM
                extractor(PlayerMessageExtractor)
            }
            entity<Long>(ShardEntityType.WorldActor.name) {
                role(Role.World.name)
                shardCount = WORLD_SHARD_NUM
                extractor(WorldMessageExtractor)
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
