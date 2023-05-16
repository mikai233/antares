package com.mikai233.player

import akka.actor.typed.javadsl.Behaviors
import com.mikai233.common.conf.GlobalProto
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.Server
import com.mikai233.common.core.components.AkkaSystem
import com.mikai233.common.core.components.NodeConfigsComponent
import com.mikai233.common.core.components.Role
import com.mikai233.common.core.components.ZookeeperConfigCenterComponent
import com.mikai233.player.component.Sharding
import com.mikai233.protocol.MsgCs
import com.mikai233.protocol.MsgSc

class PlayerNode(private val port: Int) : Launcher {
    val server: Server = Server()

    init {
        GlobalProto.init(MsgCs.MessageClientToServer.getDescriptor(), MsgSc.MessageServerToClient.getDescriptor())
        server.components {
            component {
                ZookeeperConfigCenterComponent()
            }
            component {
                NodeConfigsComponent(this, Role.Player, port)
            }
            component {
                AkkaSystem<PlayerSystemMessage>(this, Behaviors.empty())
            }
            component {
                Sharding(this@PlayerNode)
            }
        }
    }

    fun playerActorRef() = server.component<Sharding>().playerActorRef

    override fun launch() {
        server.initComponents()
    }
}

fun main(args: Array<String>) {
//    val port = args[0].toUShort()
    val port = 2333
    val playerNode = PlayerNode(port)
    playerNode.launch()
}