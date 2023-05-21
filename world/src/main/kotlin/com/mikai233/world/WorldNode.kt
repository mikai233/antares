package com.mikai233.world

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
import com.mikai233.protocol.MsgCs
import com.mikai233.protocol.MsgSc
import com.mikai233.shared.script.ScriptActor
import com.mikai233.shared.scriptActorServiceKey
import com.mikai233.world.component.WorldActorMessageDispatchers
import com.mikai233.world.component.WorldScriptSupport
import com.mikai233.world.component.WorldSharding
import com.mikai233.world.component.WorldWaker

class WorldNode(private val port: Int = 2336, private val sameJvm: Boolean = false) : Launcher {
    val server = Server()

    inner class WorldNodeGuardian(context: ActorContext<WorldSystemMessage>) :
        AbstractBehavior<WorldSystemMessage>(context) {
        private val logger = actorLogger()

        override fun createReceive(): Receive<WorldSystemMessage> {
            return newReceiveBuilder().onMessage(WorldSystemMessage::class.java) { message ->
                when (message) {
                    is SpawnScriptActorReq -> handleSpawnScriptActorReq(message)
                }
                Behaviors.same()
            }.build()
        }

        private fun handleSpawnScriptActorReq(message: SpawnScriptActorReq) {
            val scriptActor = context.spawn(Behaviors.setup { ScriptActor(it, this@WorldNode) }, ScriptActor.name())
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
                WorldActorMessageDispatchers()
            }
            component {
                ZookeeperConfigCenter()
            }
            component {
                NodeConfigsComponent(this, Role.World, port, sameJvm)
            }
            component {
                AkkaSystem(this, Behaviors.supervise(Behaviors.setup {
                    WorldNodeGuardian(it)
                }).onFailure(SupervisorStrategy.resume()))
            }
            component {
                WorldSharding(this@WorldNode)
            }
            component {
                WorldScriptSupport(this)
            }
            component {
                WorldConfigComponent(this)
            }
            component {
                WorldWaker(this@WorldNode)
            }
        }
    }

    override fun launch() {
        server.state = State.Initializing
        server.initComponents()
        server.state = State.Running
    }
}

fun main() {
    WorldNode().launch()
}