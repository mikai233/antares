package com.mikai233.player

import akka.actor.ActorRef
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.conf.GlobalProto
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.Node
import com.mikai233.common.core.Role
import com.mikai233.common.core.ShardEntityType
import com.mikai233.common.extension.startSharding
import com.mikai233.protocol.MsgCs
import com.mikai233.protocol.MsgSc
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.net.InetSocketAddress

class HomeNode(
    addr: InetSocketAddress,
    name: String,
    config: Config,
    zookeeperConnectString: String,
    sameJvm: Boolean = false,
) : Launcher, Node(addr, Role.Home, name, config, zookeeperConnectString, sameJvm) {
    init {
        GlobalProto.init(MsgCs.MessageClientToServer.getDescriptor(), MsgSc.MessageServerToClient.getDescriptor())
    }

    val playerShardRegion: ActorRef by lazy {
        system.startSharding(ShardEntityType.PlayerActor.name, Role.Home, PlayerActor)
    }

    override suspend fun launch() = start()

    override suspend fun afterStart() {
        super.afterStart()
    }
}

class Cli {
    @Parameter(names = ["-h", "--host"], description = "host")
    var host: String = GlobalEnv.machineIp

    @Parameter(names = ["-p", "--port"], description = "port")
    var port: Int = 2333

    @Parameter(names = ["-p", "--conf"], description = "conf")
    var conf: String = "home.conf"

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
    HomeNode(addr, cli.name, config, cli.zookeeper).launch()
}
