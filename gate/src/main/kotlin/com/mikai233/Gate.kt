package com.mikai233

import akka.actor.typed.javadsl.Behaviors
import com.mikai233.core.Launcher
import com.mikai233.core.Server
import com.mikai233.core.components.Cluster
import com.mikai233.core.components.Role
import com.mikai233.core.components.config.ServerConfigsComponent
import com.mikai233.core.components.config.ZookeeperConfigCenterComponent

class Gate(private val port: Int) : Launcher {
    private val server: Server = Server()

    init {
        server.components {
            component {
                ZookeeperConfigCenterComponent()
            }
            component {
                ServerConfigsComponent(Role.Gate, port)
            }
            component {
                Cluster<GateMessage>(Behaviors.empty())
            }
        }
    }

    override fun launch() {
        server.initComponents()
    }
}

fun main(args: Array<String>) {
//    val port = args[0].toUShort()
    val port = 2334
    val gate = Gate(port)
    gate.launch()
}