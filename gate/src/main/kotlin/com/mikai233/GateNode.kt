package com.mikai233

import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.mikai233.common.conf.GlobalProto
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.Server
import com.mikai233.common.core.State
import com.mikai233.common.core.components.*
import com.mikai233.common.ext.actorLogger
import com.mikai233.component.Sharding
import com.mikai233.protocol.MsgCs
import com.mikai233.protocol.MsgSc
import com.mikai233.server.NettyServer

class GateNode(private val port: Int = 2334, private val sameJvm: Boolean = false) : Launcher {
    val server: Server = Server()

    class ChannelActorGuardian(context: ActorContext<GateSystemMessage>, private val gateNode: GateNode) :
        AbstractBehavior<GateSystemMessage>(context) {
        private val logger = actorLogger()

        override fun createReceive(): Receive<GateSystemMessage> {
            return newReceiveBuilder().onMessage(GateSystemMessage::class.java) { message ->
                when (message) {
                    is SpawnChannelActorReq -> {
                        val actorRef =
                            context.spawnAnonymous(Behaviors.setup { ChannelActor(it, message.ctx, gateNode) })
                        logger.debug("spawn channel actor:{}", message.ctx.name())
                        message.replyTo.tell(SpawnChannelActorResp(actorRef))
                    }
                }
                Behaviors.same()
            }.build()
        }
    }

    init {
        GlobalProto.init(MsgCs.MessageClientToServer.getDescriptor(), MsgSc.MessageServerToClient.getDescriptor())
        server.components {
            component {
                ZookeeperConfigCenterComponent()
            }
            component {
                NodeConfigsComponent(this, Role.Gate, port, sameJvm)
            }
            component {
                AkkaSystem(this, Behaviors.setup<GateSystemMessage> {
                    ChannelActorGuardian(it, this@GateNode)
                })
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

    fun system() = server.component<AkkaSystem<GateSystemMessage>>().system

    fun playerActorRef() = server.component<Sharding>().playerActorRef

    override fun launch() {
        server.state = State.Initializing
        server.initComponents()
        server.state = State.Running
    }
}

fun main(args: Array<String>) {
//    val port = args[0].toUShort()
    GateNode().launch()
}