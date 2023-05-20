package com.mikai233.player

import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.conf.GlobalProto
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.Server
import com.mikai233.common.core.components.AkkaSystem
import com.mikai233.common.core.components.NodeConfigsComponent
import com.mikai233.common.core.components.Role
import com.mikai233.common.core.components.ZookeeperConfigCenter
import com.mikai233.common.ext.actorLogger
import com.mikai233.common.ext.registerService
import com.mikai233.player.component.MessageDispatchers
import com.mikai233.player.component.ScriptSupport
import com.mikai233.player.component.Sharding
import com.mikai233.protocol.MsgCs
import com.mikai233.protocol.MsgSc
import com.mikai233.shared.script.ScriptActor
import com.mikai233.shared.scriptActorServiceKey

class PlayerNode(private val port: Int = 2333, private val sameJvm: Boolean = false) : Launcher {
    val server: Server = Server()

    inner class PlayerNodeGuardian(context: ActorContext<PlayerSystemMessage>) :
        AbstractBehavior<PlayerSystemMessage>(context) {
        private val logger = actorLogger()

        override fun createReceive(): Receive<PlayerSystemMessage> {
            return newReceiveBuilder().onMessage(PlayerSystemMessage::class.java) { message ->
                when (message) {
                    is SpawnScriptActorReq -> handleSpawnScriptActorReq(message)
                }
                Behaviors.same()
            }.build()
        }

        private fun handleSpawnScriptActorReq(message: SpawnScriptActorReq) {
            val scriptActor = context.spawn(Behaviors.setup { ScriptActor(it, this@PlayerNode) }, ScriptActor.name())
            context.system.registerService(
                scriptActorServiceKey(GlobalEnv.machineIp, port),
                scriptActor.narrow()
            )
            message.replyTo.tell(SpawnScriptActorResp(scriptActor))
        }
    }

    init {
        GlobalProto.init(MsgCs.MessageClientToServer.getDescriptor(), MsgSc.MessageServerToClient.getDescriptor())
        server.components {
            component {
                MessageDispatchers()
            }
            component {
                ZookeeperConfigCenter()
            }
            component {
                NodeConfigsComponent(this, Role.Player, port, sameJvm)
            }
            component {
                AkkaSystem(this, Behaviors.supervise(Behaviors.setup {
                    PlayerNodeGuardian(it)
                }).onFailure(SupervisorStrategy.resume()))
            }
            component {
                Sharding(this@PlayerNode)
            }
            component {
                ScriptSupport(this)
            }
        }
    }

    fun playerActor() = server.component<Sharding>().playerActor

    override fun launch() {
        server.initComponents()
    }
}

fun main(args: Array<String>) {
//    val port = args[0].toUShort()
    PlayerNode().launch()
}