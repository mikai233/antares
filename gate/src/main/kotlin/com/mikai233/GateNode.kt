package com.mikai233

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.Server
import com.mikai233.common.core.components.*
import com.mikai233.common.ext.actorLogger
import com.mikai233.component.Sharding
import com.mikai233.server.NettyServer

class GateNode(private val port: Int) : Launcher {
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
                    is SpawnChannelActorReq -> {
                        val actorRef = context.spawnAnonymous(Behaviors.setup {
                            ChannelActor(it, message.ctx, message.player)
                        })
                        logger.debug("spawn channel actor:{}", message.ctx.name())
                        message.replyTo.tell(SpawnChannelActorResp(actorRef))
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
                NodeConfigsComponent(this, Role.Gate, port)
            }
            component {
                Cluster(this, ChannelActorGuardian.setup())
            }
            component {
                NettyConfigComponent(this)
            }
            component {
                NettyServer(this@GateNode)
            }
            component {
                Sharding(this)
            }
        }
    }

    fun system() = server.component<Cluster<GateSystemMessage>>().system

    fun player() = server.component<Sharding>().playerActorRef

    override fun launch() {
        server.initComponents()
    }
}

fun main(args: Array<String>) {
//    val port = args[0].toUShort()
    val port = 2334
    val gateNode = GateNode(port)
    gateNode.launch()
}