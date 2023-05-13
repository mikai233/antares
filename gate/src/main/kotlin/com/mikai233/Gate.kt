package com.mikai233

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.Server
import com.mikai233.common.core.components.Cluster
import com.mikai233.common.core.components.NodeConfigsComponent
import com.mikai233.common.core.components.Role
import com.mikai233.common.core.components.ZookeeperConfigCenterComponent
import com.mikai233.common.ext.actorLogger

class Gate(private val port: Int) : Launcher {
    val server: Server = Server()

    class ChannelActorGuardian(context: ActorContext<GateSystemMessage>) :
        AbstractBehavior<GateSystemMessage>(context) {
        private val logger = actorLogger()

        companion object {
            fun setup(): Behavior<GateSystemMessage> {
                return Behaviors.setup(::ChannelActorGuardian)
            }
        }

        override fun createReceive(): Receive<GateSystemMessage> {
            return newReceiveBuilder().onMessage(GateSystemMessage::class.java) { message ->
                when (message) {
                    is SpawnChannelActorAsk -> {
                        val actorRef = context.spawn(ChannelActor.setup(message.ctx), message.ctx.name())
                        logger.debug("spawn channel actor:{}", message.ctx.name())
                        message.replyTo.tell(SpawnChannelActorAns(actorRef))
                    }
                }
                Behaviors.same()
            }.build()
        }
    }

    init {
        server.components {
            component {
                ZookeeperConfigCenterComponent()
            }
            component {
                NodeConfigsComponent(Role.Gate, port)
            }
            component {
                Cluster(ChannelActorGuardian.setup())
            }
        }
    }

    fun system() = server.component<Cluster<GateSystemMessage>>().system

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