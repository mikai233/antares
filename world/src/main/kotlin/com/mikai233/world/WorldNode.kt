package com.mikai233.world

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.message.*
import com.mikai233.common.event.GameConfigUpdateEvent
import com.mikai233.common.event.GameConfigUpdatedEvent
import com.mikai233.common.event.WorldActiveEvent
import com.mikai233.common.message.world.HandoffWorld
import com.mikai233.common.message.world.PlayerLogin
import com.mikai233.common.message.world.SubscribeTopicCrossWorld
import com.mikai233.common.message.world.UnsubscribeTopicCrossWorld
import com.mikai233.common.message.world.WakeupWorldReq
import com.mikai233.protocol.ProtoSystem.GmReq
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.mikai233.asteria.core.AsteriaApplicationBuilder
import io.github.mikai233.asteria.cluster.pekko.actor
import io.github.mikai233.asteria.cluster.pekko.allocationStrategy
import io.github.mikai233.asteria.cluster.pekko.extractor
import io.github.mikai233.asteria.id.IdGenerator
import com.mikai233.world.handler.GameConfigHandler
import com.mikai233.world.handler.WorldHandler
import com.mikai233.world.handler.WorldLoginHandler
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.cluster.sharding.ShardCoordinator
import java.net.InetSocketAddress

class WorldNode(
    addr: InetSocketAddress,
    name: String,
    nodeId: String = "world-${addr.port}",
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : GameNodeRuntime(addr, listOf(Role.World), name, nodeId, config, zookeeperConnectString, sameJvm) {
    val playerSharding: ActorRef
        get() = entityShard(ShardEntityType.PlayerActor)

    val worldSharding: ActorRef
        get() = entityShard(ShardEntityType.WorldActor)

    val idGenerator: IdGenerator
        get() = services.get(IdGenerator::class)

    private val gameConfigHandler = GameConfigHandler()
    private val worldHandler = WorldHandler()
    private val worldLoginHandler = WorldLoginHandler()

    val protobufDispatcher = ActorSessionMessageDispatcher<WorldActor, PlayerSession, GeneratedMessage>(this).apply {
        register(GmReq::class, worldHandler::handleGmReq)
    }

    val internalDispatcher = ActorMessageDispatcher<WorldActor, Message>(this).apply {
        register(WakeupWorldReq::class) { actor, _ -> worldHandler.handleWakeupWorld(actor) }
        register(WorldActiveEvent::class) { actor, _ -> worldHandler.handleWorldActiveEvent(actor) }
        register(SubscribeTopicCrossWorld::class, worldHandler::handleSubscribeTopicCrossWorld)
        register(UnsubscribeTopicCrossWorld::class, worldHandler::handleUnsubscribeTopicCrossWorld)
        register(GameConfigUpdateEvent::class) { actor, _ -> gameConfigHandler.handleGameConfigUpdateEvent(actor) }
        register(GameConfigUpdatedEvent::class) { actor, _ -> gameConfigHandler.handleGameConfigUpdatedEvent(actor) }
        register(PlayerLogin::class, worldLoginHandler::handlePlayerLogin)
    }

    val gmDispatcher = ActorSessionCommandDispatcher<WorldActor, PlayerSession>().apply {
        register("testBroadcast") { actor, session, _ ->
            worldHandler.testBroadcast(actor, session)
        }
    }

    override fun modulesBeforeCluster() = listOf(EntitySerializationModule(), workerIdRuntimeModule())

    override fun modulesAfterCluster() = listOf(WorldWakerModule(this))

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
                handoffMessage = HandoffWorld
                extractor(WorldMessageExtractor)
                allocationStrategy(ShardCoordinator.LeastShardAllocationStrategy(1, 3))
                actor { runtime, _ -> WorldActor.props(runtime as WorldNode) }
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
    var conf: String = "world.conf"

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
    WorldNode(addr, cli.name, cli.nodeId ?: "world-${cli.port}", config, cli.zookeeper).launch()
}
