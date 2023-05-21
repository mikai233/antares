package com.mikai233.gate

import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.conf.GlobalProto
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.Server
import com.mikai233.common.core.State
import com.mikai233.common.core.component.*
import com.mikai233.common.ext.actorLogger
import com.mikai233.common.ext.registerService
import com.mikai233.gate.component.GateSharding
import com.mikai233.gate.component.ScriptSupport
import com.mikai233.gate.server.NettyServer
import com.mikai233.protocol.MsgCs
import com.mikai233.protocol.MsgSc
import com.mikai233.shared.script.ScriptActor
import com.mikai233.shared.scriptActorServiceKey


class GateNode(private val port: Int = 2334, private val sameJvm: Boolean = false) : Launcher {
    val server: Server = Server()

    inner class GateNodeGuardian(context: ActorContext<GateSystemMessage>) :
        AbstractBehavior<GateSystemMessage>(context) {
        private val logger = actorLogger()

        override fun createReceive(): Receive<GateSystemMessage> {
            return newReceiveBuilder().onMessage(GateSystemMessage::class.java) { message ->
                when (message) {
                    is SpawnChannelActorReq -> handleSpawnChannelActorReq(message)

                    is SpawnScriptActorReq -> handleSpawnScriptActorReq(message)
                }
                Behaviors.same()
            }.build()
        }

        private fun handleSpawnChannelActorReq(message: SpawnChannelActorReq) {
            val behavior = Behaviors.setup { ChannelActor(it, message.ctx, this@GateNode) }
            val actorRef = context.spawnAnonymous(Behaviors.supervise(behavior).onFailure(SupervisorStrategy.resume()))
            logger.debug("spawn channel actor:{}", message.ctx.name())
            message.replyTo.tell(SpawnChannelActorResp(actorRef))
        }

        private fun handleSpawnScriptActorReq(message: SpawnScriptActorReq) {
            val scriptActor = context.spawn(Behaviors.setup { ScriptActor(it, this@GateNode) }, ScriptActor.name())
            context.system.registerService(scriptActorServiceKey(GlobalEnv.machineIp, port), scriptActor.narrow())
            message.replyTo.tell(SpawnScriptActorResp((scriptActor)))
        }
    }

    init {
        GlobalProto.init(MsgCs.MessageClientToServer.getDescriptor(), MsgSc.MessageServerToClient.getDescriptor())
        server.components {
            component {
                ZookeeperConfigCenter()
            }
            component {
                NodeConfigsComponent(this, Role.Gate, port, sameJvm)
            }
            component {
                AkkaSystem(this, Behaviors.supervise(Behaviors.setup {
                    GateNodeGuardian(it)
                }).onFailure(SupervisorStrategy.resume()))
            }
            component {
                NettyConfigComponent(this)
            }
            component {
                NettyServer(this@GateNode)
            }
            component {
                GateSharding(this)
            }
            component {
                ScriptSupport(this)
            }
        }
    }

    fun system() = server.component<AkkaSystem<GateSystemMessage>>().system

    fun playerActor() = server.component<GateSharding>().playerActor

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