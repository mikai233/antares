package com.mikai233.home

import akka.actor.typed.javadsl.Behaviors
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.Server
import com.mikai233.common.core.components.Cluster
import com.mikai233.common.core.components.Role
import com.mikai233.common.core.components.config.ServerConfigsComponent
import com.mikai233.common.core.components.config.ZookeeperConfigCenterComponent

class Player(private val port: Int) : Launcher {
    private val server: Server = Server()

    init {
        server.components {
            component {
                ZookeeperConfigCenterComponent()
            }
            component {
                ServerConfigsComponent(Role.Player, port)
            }
            component {
                Cluster<HomeMessage>(Behaviors.empty())
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