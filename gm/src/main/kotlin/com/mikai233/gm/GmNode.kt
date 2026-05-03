package com.mikai233.gm

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.rpc.GameRpcProtocolDefinition
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.realmlabs.asteria.core.AsteriaApplicationBuilder
import io.github.realmlabs.asteria.cluster.pekko.PekkoSingletonStartup
import io.github.realmlabs.asteria.cluster.pekko.extractor
import io.github.realmlabs.asteria.cluster.pekko.singletonStartup
import kotlinx.coroutines.runBlocking
import org.apache.pekko.actor.ActorRef
import java.net.InetSocketAddress

class GmNode(
    addr: InetSocketAddress,
    name: String,
    nodeId: String = "gm-${addr.port}",
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : GameNodeRuntime(addr, listOf(Role.Gm), name, nodeId, config, zookeeperConnectString, sameJvm) {

    val playerSharding: ActorRef
        get() = entityShard(ShardEntityType.PlayerActor)

    val worldSharding: ActorRef
        get() = entityShard(ShardEntityType.WorldActor)

    val workerSingletonProxy: ActorRef
        get() = singletonActor(Singleton.Worker)

    override fun modulesAfterCluster() = listOf(GmRuntimeModule(this))

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

    @Parameter(names = ["-i", "--node-id"], description = "runtime node id")
    var nodeId: String? = null
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
    GmNode(addr, cli.name, cli.nodeId ?: "gm-${cli.port}", config, cli.zookeeper).launch()
}
