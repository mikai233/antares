package com.mikai233.gm

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.message.PlayerMessageExtractor
import com.mikai233.common.message.WorldMessageExtractor
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.mikai233.asteria.core.AsteriaApplicationBuilder
import io.github.mikai233.asteria.cluster.pekko.PekkoSingletonStartup
import io.github.mikai233.asteria.cluster.pekko.extractor
import io.github.mikai233.asteria.cluster.pekko.singletonStartup
import kotlinx.coroutines.runBlocking
import org.apache.pekko.actor.ActorRef
import java.net.InetSocketAddress

class GmNode(
    addr: InetSocketAddress,
    name: String,
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : GameNodeRuntime(addr, listOf(Role.Gm), name, config, zookeeperConnectString, sameJvm) {

    val playerSharding: ActorRef
        get() = entityShard(ShardEntityType.PlayerActor)

    val worldSharding: ActorRef
        get() = entityShard(ShardEntityType.WorldActor)

    val scriptRouter: ActorRef
        get() = services.get(GmRuntime::class).scriptRouter

    val workerSingletonProxy: ActorRef
        get() = singletonActor(Singleton.Worker)

    val scriptExecutionManager: ActorRef
        get() = services.get(GmRuntime::class).scriptExecutionManager

    override fun modulesAfterCluster() = listOf(GmRuntimeModule(this))

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
