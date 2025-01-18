package com.mikai233.global

import akka.actor.ActorRef
import akka.protobufv3.internal.GeneratedMessage
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.*
import com.mikai233.common.entity.EntityKryoPool
import com.mikai233.common.extension.startShardingProxy
import com.mikai233.common.extension.startSingleton
import com.mikai233.common.message.*
import com.mikai233.common.message.global.worker.HandoffWorker
import com.mikai233.global.actor.WorkerActor
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.net.InetSocketAddress
import kotlin.concurrent.thread

class GlobalNode(
    addr: InetSocketAddress,
    name: String,
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false
) : Launcher, Node(addr, listOf(Role.Global), name, config, zookeeperConnectString, sameJvm) {

    lateinit var playerSharding: ActorRef
        private set

    lateinit var worldSharding: ActorRef
        private set

    lateinit var workerActor: ActorRef
        private set

    private val handlerReflect = MessageHandlerReflect("com.mikai233.global.handler")

    val protobufDispatcher = MessageDispatcher(GeneratedMessage::class, handlerReflect)

    val internalDispatcher = MessageDispatcher(Message::class, handlerReflect)

    override suspend fun launch() = start()

    override suspend fun beforeStart() {
        super.beforeStart()
        thread { EntityKryoPool }
    }

    override suspend fun afterStart() {
        startWorkerSingleton()
        startPlayerSharding()
        startWorldSharding()
        super.afterStart()
    }

    private fun startPlayerSharding() {
        playerSharding =
            system.startShardingProxy(ShardEntityType.PlayerActor.name, Role.Player, PlayerMessageExtractor)
    }

    private fun startWorldSharding() {
        worldSharding = system.startShardingProxy(ShardEntityType.WorldActor.name, Role.World, WorldMessageExtractor)
    }

    private fun startWorkerSingleton() {
        workerActor =
            system.startSingleton(Singleton.Worker.actorName, Role.Global, WorkerActor.props(this), HandoffWorker)
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
