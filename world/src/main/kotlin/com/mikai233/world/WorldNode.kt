package com.mikai233.world

import SnowflakeGenerator
import akka.actor.ActorRef
import akka.cluster.sharding.ShardCoordinator
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.extension.ask
import com.mikai233.common.extension.startSharding
import com.mikai233.common.extension.startShardingProxy
import com.mikai233.common.extension.startSingletonProxy
import com.mikai233.common.message.Message
import com.mikai233.common.message.MessageDispatcher
import com.mikai233.shared.entity.EntityKryoPool
import com.mikai233.shared.message.PlayerMessageExtractor
import com.mikai233.shared.message.WorldMessageExtractor
import com.mikai233.shared.message.global.worker.WorkerIdReq
import com.mikai233.shared.message.global.worker.WorkerIdResp
import com.mikai233.shared.message.world.HandoffWorld
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.net.InetSocketAddress
import kotlin.concurrent.thread

class WorldNode(
    addr: InetSocketAddress,
    name: String,
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false
) : Launcher, Node(addr, listOf(Role.World), name, config, zookeeperConnectString, sameJvm) {
    lateinit var playerSharding: ActorRef
        private set

    lateinit var worldSharding: ActorRef
        private set

    lateinit var workerSingletonProxy: ActorRef
        private set

    lateinit var snowflakeGenerator: SnowflakeGenerator
        private set

    val protobufDispatcher = MessageDispatcher(GeneratedMessage::class, "com.mikai233.world.handler")

    val internalDispatcher = MessageDispatcher(Message::class, "com.mikai233.world.handler")

    override suspend fun launch() = start()

    override suspend fun beforeStart() {
        super.beforeStart()
        thread { EntityKryoPool }
    }

    override suspend fun afterStart() {
        startWorkerSingletonProxy()
        startSnowflakeGenerator()
        startPlayerSharding()
        startWorldSharding()
        super.afterStart()
    }

    private fun startPlayerSharding() {
        playerSharding =
            system.startShardingProxy(ShardEntityType.PlayerActor.name, Role.Player, PlayerMessageExtractor)
    }

    private fun startWorldSharding() {
        worldSharding = system.startSharding(
            ShardEntityType.WorldActor.name,
            Role.World,
            WorldActor.props(this),
            HandoffWorld,
            WorldMessageExtractor,
            ShardCoordinator.LeastShardAllocationStrategy(1, 3),
        )
    }

    private fun startWorkerSingletonProxy() {
        workerSingletonProxy = system.startSingletonProxy(Singleton.Worker.actorName, Role.Global)
    }

    private suspend fun startSnowflakeGenerator() {
        val resp = workerSingletonProxy.ask<WorkerIdResp>(WorkerIdReq(addr.toString())).getOrThrow()
        snowflakeGenerator = SnowflakeGenerator(resp.id.toLong())
        logger.info("apply worker id: {} for addr: {}", resp.id, addr)
    }
}

class Cli {
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
    JCommander.newBuilder()
        .addObject(cli)
        .build()
        .parse(*args)
    val addr = InetSocketAddress(cli.host, cli.port)
    val config = ConfigFactory.load(cli.conf)
    WorldNode(addr, cli.name, config, cli.zookeeper).launch()
}