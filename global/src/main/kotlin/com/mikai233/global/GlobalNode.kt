package com.mikai233.global

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.entity.EntityKryoPool
import com.mikai233.common.message.PlayerMessageExtractor
import com.mikai233.common.message.global.worker.HandoffWorker
import com.mikai233.common.message.WorldMessageExtractor
import com.mikai233.global.actor.WorkerActor
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.mikai233.asteria.core.AsteriaApplicationBuilder
import io.github.mikai233.asteria.cluster.pekko.actor
import io.github.mikai233.asteria.cluster.pekko.extractor
import org.apache.pekko.actor.ActorRef
import java.net.InetSocketAddress
import kotlin.concurrent.thread

class GlobalNode(
    addr: InetSocketAddress,
    name: String,
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : Node(addr, listOf(Role.Global), name, config, zookeeperConnectString, sameJvm) {

    lateinit var playerSharding: ActorRef
        private set

    lateinit var worldSharding: ActorRef
        private set

    lateinit var workerActor: ActorRef
        private set

    override suspend fun beforeStart() {
        super.beforeStart()
        thread { EntityKryoPool }
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
            singleton(Singleton.Worker.actorName) {
                role(Role.Global.name)
                handoffMessage = HandoffWorker
                actor { runtime, _ -> WorkerActor.props(runtime as GlobalNode) }
            }
        }
    }

    override suspend fun afterStart() {
        workerActor = singletonActor(Singleton.Worker)
        playerSharding = entityShard(ShardEntityType.PlayerActor)
        worldSharding = entityShard(ShardEntityType.WorldActor)
        super.afterStart()
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
    GlobalNode(addr, cli.name, config, cli.zookeeper).launch()
}
