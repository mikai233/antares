package com.mikai233.global

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.message.global.worker.HandoffWorker
import com.mikai233.common.rpc.GameRpcProtocolDefinition
import com.mikai233.global.actor.WorkerActor
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.realmlabs.asteria.core.AsteriaApplicationBuilder
import io.github.realmlabs.asteria.cluster.pekko.actor
import io.github.realmlabs.asteria.cluster.pekko.extractor
import org.apache.pekko.actor.ActorRef
import java.net.InetSocketAddress

class GlobalNode(
    addr: InetSocketAddress,
    name: String,
    nodeId: String = "global-${addr.port}",
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : GameNodeRuntime(addr, listOf(Role.Global), name, nodeId, config, zookeeperConnectString, sameJvm) {

    val playerSharding: ActorRef
        get() = entityShard(ShardEntityType.PlayerActor)

    val worldSharding: ActorRef
        get() = entityShard(ShardEntityType.WorldActor)

    val workerActor: ActorRef
        get() = singletonActor(Singleton.Worker)

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
                handoffMessage = HandoffWorker
                actor { runtime, _ -> WorkerActor.props(runtime as GlobalNode) }
            }
        }
    }

}

private class Cli {
    @Parameter(names = ["-h", "--host"], description = "host")
    var host: String = GlobalEnv.machineIp

    @Parameter(names = ["-p", "--port"], description = "port")
    var port: Int = 2335

    @Parameter(names = ["-c", "--conf"], description = "conf")
    var conf: String = "global.conf"

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
    GlobalNode(addr, cli.name, cli.nodeId ?: "global-${cli.port}", config, cli.zookeeper).launch()
}
