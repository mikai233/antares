package com.mikai233.gm

import akka.actor.ActorRef
import akka.routing.FromConfig
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.extension.startShardingProxy
import com.mikai233.common.extension.startSingletonProxy
import com.mikai233.common.message.PlayerMessageExtractor
import com.mikai233.common.message.WorldMessageExtractor
import com.mikai233.gm.web.Engine
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.net.InetSocketAddress

class GmNode(
    addr: InetSocketAddress,
    name: String,
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : Launcher, Node(addr, listOf(Role.Gm), name, config, zookeeperConnectString, sameJvm) {

    lateinit var playerSharding: ActorRef
        private set

    lateinit var worldSharding: ActorRef
        private set

    private lateinit var engine: Engine

    lateinit var scriptRouter: ActorRef
        private set

    lateinit var workerSingletonProxy: ActorRef
        private set

    override suspend fun launch() = start()

    override suspend fun afterStart() {
        startWebEngine()
        startScriptRouter()
        startWorkerSingletonProxy()
        startPlayerSharding()
        startWorldSharding()
        startMonitor()
        super.afterStart()
    }

    private fun startPlayerSharding() {
        playerSharding =
            system.startShardingProxy(ShardEntityType.PlayerActor.name, Role.Player, PlayerMessageExtractor)
    }

    private fun startWorldSharding() {
        worldSharding = system.startShardingProxy(ShardEntityType.WorldActor.name, Role.World, WorldMessageExtractor)
    }

    private fun startScriptRouter() {
        scriptRouter = system.actorOf(FromConfig.getInstance().props(), "scriptActorRouter")
    }

    private fun startWorkerSingletonProxy() {
        workerSingletonProxy = system.startSingletonProxy(Singleton.Worker.actorName, Role.Global)
    }

    private fun startWebEngine() {
        engine = Engine(this)
        engine.start()
    }

    private fun startMonitor() {
        system.actorOf(MonitorActor.props(this), "monitorActor")
    }
}

private class Cli {
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
    @Suppress("SpreadOperator")
    JCommander.newBuilder()
        .addObject(cli)
        .build()
        .parse(*args)
    val addr = InetSocketAddress(cli.host, cli.port)
    val config = ConfigFactory.load(cli.conf)
    GmNode(addr, cli.name, config, cli.zookeeper).launch()
}
