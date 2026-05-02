package com.mikai233.player

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.entity.EntityKryoPool
import com.mikai233.common.extension.startSharding
import com.mikai233.common.extension.startShardingProxy
import com.mikai233.common.message.*
import com.mikai233.common.message.player.HandoffPlayer
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.mikai233.asteria.id.IdGenerator
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.cluster.sharding.ShardCoordinator
import java.net.InetSocketAddress
import kotlin.concurrent.thread

class PlayerNode(
    addr: InetSocketAddress,
    name: String,
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : Launcher, Node(addr, listOf(Role.Player), name, config, zookeeperConnectString, sameJvm) {
    lateinit var playerSharding: ActorRef
        private set

    lateinit var worldSharding: ActorRef
        private set

    lateinit var idGenerator: IdGenerator
        private set

    private val handlerReflect = MessageHandlerReflect("com.mikai233.player.handler")

    val protobufDispatcher = MessageDispatcher(GeneratedMessage::class, handlerReflect, 1)

    val internalDispatcher = MessageDispatcher(Message::class, handlerReflect, 1)

    val gmDispatcher = GmDispatcher(handlerReflect)

    override suspend fun launch() = start()

    override suspend fun beforeStart() {
        super.beforeStart()
        thread { EntityKryoPool }
    }

    override suspend fun afterStart() {
        installWorkerIdRuntime()
        idGenerator = services.get(IdGenerator::class)
        startPlayerSharding()
        startWorldSharding()
        super.afterStart()
    }

    private fun startPlayerSharding() {
        playerSharding = system.startSharding(
            ShardEntityType.PlayerActor.name,
            Role.Player,
            PlayerActor.props(this),
            HandoffPlayer,
            PlayerMessageExtractor,
            ShardCoordinator.LeastShardAllocationStrategy(1, 3),
        )
    }

    private fun startWorldSharding() {
        worldSharding = system.startShardingProxy(ShardEntityType.WorldActor.name, Role.World, WorldMessageExtractor)
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
    PlayerNode(addr, cli.name, config, cli.zookeeper).launch()
}
