package com.mikai233.player

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
import com.mikai233.common.event.PlayerCreateEvent
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.common.message.player.PlayerCreateReq
import com.mikai233.common.message.player.PlayerLoginReq
import com.mikai233.common.message.player.HandoffPlayer
import com.mikai233.player.handler.GameConfigHandler
import com.mikai233.player.handler.LoginHandler
import com.mikai233.player.handler.PlayerHandler
import com.mikai233.player.handler.TestHandler
import com.mikai233.protocol.ProtoSystem.GmReq
import com.mikai233.protocol.ProtoTest.TestReq
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.mikai233.asteria.core.AsteriaApplicationBuilder
import io.github.mikai233.asteria.cluster.pekko.actor
import io.github.mikai233.asteria.cluster.pekko.allocationStrategy
import io.github.mikai233.asteria.cluster.pekko.extractor
import io.github.mikai233.asteria.id.IdGenerator
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.cluster.sharding.ShardCoordinator
import java.net.InetSocketAddress

class PlayerNode(
    addr: InetSocketAddress,
    name: String,
    nodeId: String = "player-${addr.port}",
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : GameNodeRuntime(addr, listOf(Role.Player), name, nodeId, config, zookeeperConnectString, sameJvm) {
    val playerSharding: ActorRef
        get() = entityShard(ShardEntityType.PlayerActor)

    val worldSharding: ActorRef
        get() = entityShard(ShardEntityType.WorldActor)

    val idGenerator: IdGenerator
        get() = services.get(IdGenerator::class)

    private val gameConfigHandler = GameConfigHandler()
    private val loginHandler = LoginHandler()
    private val playerHandler = PlayerHandler()
    private val testHandler = TestHandler()

    val protobufDispatcher = ActorMessageDispatcher<PlayerActor, GeneratedMessage>(this).apply {
        register(GmReq::class, playerHandler::handleGmReq)
        register(TestReq::class, testHandler::handleTestReq)
    }

    val internalDispatcher = ActorMessageDispatcher<PlayerActor, Message>(this).apply {
        register(GameConfigUpdateEvent::class) { actor, _ -> gameConfigHandler.handleGameConfigUpdate(actor) }
        register(PlayerLoginReq::class, loginHandler::handlePlayerLoginReq)
        register(PlayerCreateReq::class, loginHandler::handlePlayerCreateReq)
        register(PlayerLoginEvent::class) { actor, _ -> playerHandler.handlePlayerLoginEvent(actor) }
        register(PlayerCreateEvent::class) { actor, _ -> playerHandler.handlePlayerCreateEvent(actor) }
        register(GameConfigUpdatedEvent::class) { actor, _ -> playerHandler.handleGameConfigUpdatedEvent(actor) }
    }

    val gmDispatcher = ActorCommandDispatcher<PlayerActor>().apply {
        register("testGm", playerHandler::handleTestGm)
    }

    override fun modulesBeforeCluster() = listOf(EntitySerializationModule(), workerIdRuntimeModule())

    override fun configureRuntime(builder: AsteriaApplicationBuilder) {
        builder.apply {
            entity<Long>(ShardEntityType.PlayerActor.name) {
                role(Role.Player.name)
                shardCount = PLAYER_SHARD_NUM
                handoffMessage = HandoffPlayer
                extractor(PlayerMessageExtractor)
                allocationStrategy(ShardCoordinator.LeastShardAllocationStrategy(1, 3))
                actor { runtime, _ -> PlayerActor.props(runtime as PlayerNode) }
            }
            entity<Long>(ShardEntityType.WorldActor.name) {
                role(Role.World.name)
                shardCount = WORLD_SHARD_NUM
                extractor(WorldMessageExtractor)
            }
        }
    }

}

private class Cli {
    @Parameter(names = ["-h", "--host"], description = "host")
    var host: String = GlobalEnv.machineIp

    @Parameter(names = ["-p", "--port"], description = "port")
    var port: Int = 2333

    @Parameter(names = ["-c", "--conf"], description = "conf")
    var conf: String = "player.conf"

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
    PlayerNode(addr, cli.name, cli.nodeId ?: "player-${cli.port}", config, cli.zookeeper).launch()
}
