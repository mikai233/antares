package com.mikai233.gm

import akka.actor.ActorRef
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.Node
import com.mikai233.common.core.Role
import com.mikai233.common.core.ShardEntityType
import com.mikai233.common.extension.startShardingProxy
import com.mikai233.gm.web.Engine
import com.mikai233.shared.message.PlayerMessageExtractor
import com.mikai233.shared.message.WorldMessageExtractor
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.net.InetSocketAddress

class GmNode(
    addr: InetSocketAddress,
    name: String,
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false
) : Launcher, Node(addr, Role.Gm, name, config, zookeeperConnectString, sameJvm) {

    lateinit var playerSharding: ActorRef
        private set

    lateinit var worldSharding: ActorRef
        private set

    private lateinit var engine: Engine


    override suspend fun launch() = start()

    override suspend fun afterStart() {
        startWebEngine()
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

    private fun startWebEngine() {
        engine = Engine(this)
        engine.start()
    }
}

class Cli {
    @Parameter(names = ["-h", "--host"], description = "host")
    var host: String = GlobalEnv.machineIp

    @Parameter(names = ["-p", "--port"], description = "port")
    var port: Int = 2336

    @Parameter(names = ["-c", "--conf"], description = "conf")
    var conf: String = "gm.conf"

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
    GmNode(addr, cli.name, config, cli.zookeeper).launch()
}