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
import com.mikai233.common.ext.closeableSingle
import com.mikai233.common.ext.registerService
import com.mikai233.common.inject.XKoin
import com.mikai233.gate.component.GateSharding
import com.mikai233.gate.component.ScriptSupport
import com.mikai233.gate.server.NettyServer
import com.mikai233.protocol.MsgCs
import com.mikai233.protocol.MsgSc
import com.mikai233.shared.message.ChannelMessage
import com.mikai233.shared.script.ScriptActor
import com.mikai233.shared.scriptActorServiceKey
import org.koin.core.component.get
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.logger.slf4jLogger


class GateNode(private val port: Int = 2334, private val sameJvm: Boolean = false) : Launcher {
    lateinit var koin: XKoin
        private set

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
            val behavior = Behaviors.setup<ChannelMessage> {
                Behaviors.withTimers { timers ->
                    Behaviors.withStash(100) { buffer ->
                        ChannelActor(it, message.ctx, timers, buffer, koin)
                    }
                }
            }
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
        XKoin(koinApplication {
            this@GateNode.koin = XKoin(this)
            slf4jLogger()
            modules(serverModule())
        })
    }

    override fun launch() {
        val server = koin.get<Server>()
        server.state = State.Initializing
        server.onInit()
        server.state = State.Running
    }

    private fun serverModule() = module(createdAtStart = true) {
        single { this@GateNode }
        single { Server(koin) }
        closeableSingle { ZookeeperConfigCenter() }
        single { NodeConfigHolder(koin, Role.Gate, port, sameJvm) }
        single {
            AkkaSystem(koin, Behaviors.supervise(Behaviors.setup {
                GateNodeGuardian(it)
            }).onFailure(SupervisorStrategy.resume()))
        }
        single { NettyConfigHolder(koin) }
        closeableSingle { NettyServer(koin) }
        single { GateSharding(koin) }
        single { ScriptSupport(koin) }
    }
}

fun main(args: Array<String>) {
//    val port = args[0].toUShort()
    GateNode().launch()
}
