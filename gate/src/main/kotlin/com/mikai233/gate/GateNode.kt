package com.mikai233.gate

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.config.ConfigCache
import com.mikai233.common.config.NettyConfig
import com.mikai233.common.config.nettyConfigPath
import com.mikai233.common.core.*
import com.mikai233.common.message.*
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.mikai233.asteria.core.AsteriaApplicationBuilder
import io.github.mikai233.asteria.cluster.pekko.extractor
import io.github.mikai233.asteria.gateway.netty.NettyGatewayServerOptions
import io.github.mikai233.asteria.gateway.netty.NettyTcpGatewayServerTransport
import org.apache.pekko.actor.ActorRef
import java.net.InetSocketAddress
import kotlin.concurrent.thread


class GateNode(
    addr: InetSocketAddress,
    name: String,
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : Node(addr, listOf(Role.Gate), name, config, zookeeperConnectString, sameJvm) {
    lateinit var playerSharding: ActorRef
        private set

    lateinit var worldSharding: ActorRef
        private set

    private val handlerReflect = MessageHandlerReflect("com.mikai233.gate.handler")

    val protobufDispatcher = MessageDispatcher(GeneratedMessage::class, handlerReflect, 1)

    val internalDispatcher = MessageDispatcher(Message::class, handlerReflect, 1)

    private val nettyConfigsCache =
        ConfigCache(zookeeper, nettyConfigPath(addr.hostString, addr.port), NettyConfig::class)

    val nettyConfig get() = nettyConfigsCache.config

    val protocolCodec = GateProtocolCodec()

    private lateinit var gatewayTransport: NettyTcpGatewayServerTransport

    override suspend fun beforeStart() {
        thread { MessageForward }
        super.beforeStart()
    }

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

    override suspend fun afterStart() {
        playerSharding = entityShard(ShardEntityType.PlayerActor)
        worldSharding = entityShard(ShardEntityType.WorldActor)
        super.afterStart()
        startGatewayTransport()
    }

    private suspend fun startGatewayTransport() {
        gatewayTransport = NettyTcpGatewayServerTransport(
            NettyGatewayServerOptions(
                host = nettyConfig.host,
                port = nettyConfig.port,
                maxFrameLength = 1024 * 100,
            ),
            scope = coroutineScope,
            pipelineInstaller = GateNettyPipeline.installer(protocolCodec),
        )
        gatewayTransport.start(GateTransportHandler(this))
        addStateListener(State.Stopping) { gatewayTransport.stop() }
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
    GateNode(addr, cli.name, config, cli.zookeeper).launch()
}
