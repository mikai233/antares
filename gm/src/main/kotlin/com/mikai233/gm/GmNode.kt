package com.mikai233.gm

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.message.PlayerMessageExtractor
import com.mikai233.common.message.WorldMessageExtractor
import com.mikai233.gm.script.ScriptExecutionManagerActor
import com.mikai233.gm.web.GmWebServer
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.mikai233.asteria.core.AsteriaApplicationBuilder
import io.github.mikai233.asteria.cluster.pekko.PekkoSingletonStartup
import io.github.mikai233.asteria.cluster.pekko.extractor
import io.github.mikai233.asteria.cluster.pekko.singletonStartup
import kotlinx.coroutines.runBlocking
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.routing.FromConfig
import java.net.InetSocketAddress

class GmNode(
    addr: InetSocketAddress,
    name: String,
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : Node(addr, listOf(Role.Gm), name, config, zookeeperConnectString, sameJvm) {

    lateinit var playerSharding: ActorRef
        private set

    lateinit var worldSharding: ActorRef
        private set

    private lateinit var webServer: GmWebServer

    lateinit var scriptRouter: ActorRef
        private set

    lateinit var workerSingletonProxy: ActorRef
        private set

    lateinit var scriptExecutionManager: ActorRef
        private set

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
            singleton(Singleton.Worker.actorName) {
                role(Role.Global.name)
                singletonStartup(PekkoSingletonStartup.Proxy)
            }
        }
    }

    override suspend fun afterStart() {
        startScriptRouter()
        workerSingletonProxy = singletonActor(Singleton.Worker)
        playerSharding = entityShard(ShardEntityType.PlayerActor)
        worldSharding = entityShard(ShardEntityType.WorldActor)
        startMonitor()
        startScriptExecutionManager()
        startWebServer()
        super.afterStart()
    }

    private fun startScriptRouter() {
        scriptRouter = system.actorOf(FromConfig.getInstance().props(), "scriptActorRouter")
    }

    private fun startWebServer() {
        webServer = GmWebServer(this)
        webServer.start()
        addStateListener(State.Stopping) {
            webServer.stop()
        }
    }

    private fun startMonitor() {
        system.actorOf(MonitorActor.props(this), "monitorActor")
    }

    private fun startScriptExecutionManager() {
        scriptExecutionManager = system.actorOf(
            ScriptExecutionManagerActor.props(this),
            ScriptExecutionManagerActor.NAME,
        )
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

fun main(args: Array<String>) = runBlocking {
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
