package com.mikai233.player

import akka.actor.ActorRef
import akka.cluster.sharding.ShardCoordinator
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.conf.GlobalProto
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.Node
import com.mikai233.common.core.Role
import com.mikai233.common.core.ShardEntityType
import com.mikai233.common.extension.startSharding
import com.mikai233.common.extension.startShardingProxy
import com.mikai233.protocol.MsgCs
import com.mikai233.protocol.MsgSc
import com.mikai233.shared.message.PlayerMessageExtractor
import com.mikai233.shared.message.player.HandoffPlayer
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.net.InetSocketAddress

class PlayerNode(
    addr: InetSocketAddress,
    name: String,
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : Launcher, Node(addr, Role.Player, name, config, zookeeperConnectString, sameJvm) {
    init {
        GlobalProto.init(MsgCs.MessageClientToServer.getDescriptor(), MsgSc.MessageServerToClient.getDescriptor())
    }

    lateinit var playerSharding: ActorRef
        private set

    lateinit var worldSharding: ActorRef
        private set

    override suspend fun launch() = start()

    override suspend fun afterStart() {
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
            PlayerMessageExtractor(3000),
            ShardCoordinator.LeastShardAllocationStrategy(1, 3),
        )
    }

    private fun startWorldSharding() {
        worldSharding = system.startShardingProxy(ShardEntityType.WorldActor.name)
    }
}

class Cli {
    @Parameter(names = ["-h", "--host"], description = "host")
    var host: String = GlobalEnv.machineIp

    @Parameter(names = ["-p", "--port"], description = "port")
    var port: Int = 2333

    @Parameter(names = ["-p", "--conf"], description = "conf")
    var conf: String = "player.conf"

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
    PlayerNode(addr, cli.name, config, cli.zookeeper).launch()
}
