package com.mikai233.player

import akka.actor.typed.javadsl.Behaviors
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.Server
import com.mikai233.common.core.components.Cluster
import com.mikai233.common.core.components.NodeConfigsComponent
import com.mikai233.common.core.components.Role
import com.mikai233.common.core.components.ZookeeperConfigCenterComponent

class Player(private val port: Int) : Launcher {
    private val server: Server = Server()

    init {
        server.components {
            component {
                ZookeeperConfigCenterComponent()
            }
            component {
                NodeConfigsComponent(Role.Player, port)
            }
            component {
                Cluster<PlayerSystemMessage>(Behaviors.empty())
            }
        }
    }

    override fun launch() {
        server.initComponents()
    }
}

fun main(args: Array<String>) {
//    val port = args[0].toUShort()
    val port = 2333
    val player = Player(port)
    player.launch()
}