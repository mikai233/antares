package com.mikai233.world

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.entity.EntityKryoPool
import com.mikai233.common.message.*
import com.mikai233.common.message.world.HandoffWorld
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.mikai233.asteria.cluster.pekko.actor
import io.github.mikai233.asteria.cluster.pekko.allocationStrategy
import io.github.mikai233.asteria.cluster.pekko.extractor
import io.github.mikai233.asteria.id.IdGenerator
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.cluster.sharding.ShardCoordinator
import java.net.InetSocketAddress
import kotlin.concurrent.thread

class WorldNode(
    addr: InetSocketAddress,
    name: String,
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : Node(addr, listOf(Role.World), name, config, zookeeperConnectString, sameJvm) {
    lateinit var playerSharding: ActorRef
        private set

    lateinit var worldSharding: ActorRef
        private set

    lateinit var idGenerator: IdGenerator
        private set

    private val handlerReflect = MessageHandlerReflect("com.mikai233.world.handler")

    val protobufDispatcher = MessageDispatcher(GeneratedMessage::class, handlerReflect, 2)

    val internalDispatcher = MessageDispatcher(Message::class, handlerReflect, 1)

    val gmDispatcher = GmDispatcher(handlerReflect)

    override suspend fun beforeStart() {
        super.beforeStart()
        thread { EntityKryoPool }
    }

    override fun runtimeModulesBeforePekko() = listOf(workerIdRuntimeModule())

    override fun runtimeTopology() = buildRuntimeTopology {
        entity<Long>(ShardEntityType.PlayerActor.name) {
            role(Role.Player.name)
            shardCount = PLAYER_SHARD_NUM
            extractor(PlayerMessageExtractor)
        }
        entity<Long>(ShardEntityType.WorldActor.name) {
            role(Role.World.name)
            shardCount = WORLD_SHARD_NUM
            handoffMessage = HandoffWorld
            extractor(WorldMessageExtractor)
            allocationStrategy(ShardCoordinator.LeastShardAllocationStrategy(1, 3))
            actor { runtime, _ -> WorldActor.props(runtime as WorldNode) }
        }
    }

    override suspend fun afterStart() {
        idGenerator = services.get(IdGenerator::class)
        playerSharding = entityShard(ShardEntityType.PlayerActor)
        worldSharding = entityShard(ShardEntityType.WorldActor)
        startWorldWaker()
        super.afterStart()
    }

    private fun startWorldWaker() {
        system.actorOf(WorldWaker.props(this), "worldWaker")
    }
}

private class Cli {
    @Parameter(names = ["-h", "--host"], description = "host")
    var host: String = GlobalEnv.machineIp

    @Parameter(names = ["-p", "--port"], description = "port")
    var port: Int = 2336

    @Parameter(names = ["-c", "--conf"], description = "conf")
    var conf: String = "world.conf"

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
    WorldNode(addr, cli.name, config, cli.zookeeper).launch()
}
