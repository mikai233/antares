package com.mikai233.gate

import akka.actor.ActorRef
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.core.config.ConfigCache
import com.mikai233.common.core.config.NettyConfig
import com.mikai233.common.core.config.nettyConfigPath
import com.mikai233.common.extension.startShardingProxy
import com.mikai233.common.message.Message
import com.mikai233.common.message.MessageDispatcher
import com.mikai233.common.message.MessageHandlerReflect
import com.mikai233.gate.server.NettyServer
import com.mikai233.shared.message.PlayerMessageExtractor
import com.mikai233.shared.message.WorldMessageExtractor
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.net.InetSocketAddress
import kotlin.concurrent.thread


class GateNode(
    addr: InetSocketAddress,
    name: String,
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false
) : Launcher, Node(addr, listOf(Role.Gate), name, config, zookeeperConnectString, sameJvm) {
    lateinit var playerSharding: ActorRef
        private set

    lateinit var worldSharding: ActorRef
        private set

    private val handlerReflect = MessageHandlerReflect("com.mikai233.gate.handler")

    val protobufDispatcher = MessageDispatcher(GeneratedMessage::class, handlerReflect)

    val internalDispatcher = MessageDispatcher(Message::class, handlerReflect)

    private val nettyServer = NettyServer(this)

    private val nettyConfigsCache =
        ConfigCache(zookeeper, nettyConfigPath(addr.hostString, addr.port), NettyConfig::class)

    val nettyConfig get() = nettyConfigsCache.config

    override suspend fun launch() = start()

    override suspend fun beforeStart() {
        thread { MessageForward }
        nettyServer.start()
        super.beforeStart()
        addStateListener(State.Stopping) { nettyServer.close() }
    }

    override suspend fun afterStart() {
        startPlayerSharding()
        startWorldSharding()
        super.afterStart()
    }

    private fun startPlayerSharding() {
        playerSharding =
            system.startShardingProxy(ShardEntityType.PlayerActor.name, Role.Player, PlayerMessageExtractor)
    }

    private fun startWorldSharding() {
        worldSharding = system.startShardingProxy(ShardEntityType.WorldActor.name, Role.World, WorldMessageExtractor)
    }
}

class Cli {
    @Parameter(names = ["-h", "--host"], description = "host")
    var host: String = GlobalEnv.machineIp

    @Parameter(names = ["-p", "--port"], description = "port")
    var port: Int = 2334

    @Parameter(names = ["-c", "--conf"], description = "conf")
    var conf: String = "world.conf"

    @Parameter(names = ["-z", "--zookeeper"], description = "zookeeper")
    var zookeeper: String = GlobalEnv.zkConnect

    @Parameter(names = ["-n", "--name"], description = "system name")
    var name: String = GlobalEnv.SYSTEM_NAME
}

suspend fun main(args: Array<String>) {
    val cli = Cli()
    JCommander.newBuilder()
        .addObject(cli)
        .build()
        .parse(*args)
    val addr = InetSocketAddress(cli.host, cli.port)
    val config = ConfigFactory.load(cli.conf)
    GateNode(addr, cli.name, config, cli.zookeeper).launch()
}