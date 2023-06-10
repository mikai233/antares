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
import com.mikai233.common.core.State
import com.mikai233.common.core.component.*
import com.mikai233.common.ext.actorLogger
import com.mikai233.common.ext.closeableSingle
import com.mikai233.common.ext.registerService
import com.mikai233.common.inject.XKoin
import com.mikai233.player.component.PlayerActorDispatchers
import com.mikai233.player.component.PlayerScriptSupport
import com.mikai233.player.component.PlayerSharding
import com.mikai233.protocol.MsgCs
import com.mikai233.protocol.MsgSc
import com.mikai233.shared.component.ExcelConfigHolder
import com.mikai233.shared.script.ScriptActor
import com.mikai233.shared.scriptActorServiceKey
import org.koin.core.component.get
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.logger.slf4jLogger

class PlayerNode(private val port: Int = 2337, private val sameJvm: Boolean = false) : Launcher {
    lateinit var koin: XKoin
        private set

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
        koinApplication {
            this@PlayerNode.koin = XKoin(this)
            slf4jLogger()
            modules(serverModule())
        }
    }

    override fun launch() {
        val server = koin.get<Server>()
        server.state = State.Initializing
        server.onInit()
        server.state = State.Running
    }

    private fun serverModule() = module(createdAtStart = true) {
        single { this@PlayerNode }
        single { Server(koin) }
        single { PlayerActorDispatchers(koin) }
        closeableSingle { ZookeeperConfigCenter() }
        closeableSingle { MongoHolder(koin) }
        single { NodeConfigHolder(koin, Role.Player, port, sameJvm) }
        single { ExcelConfigHolder(koin) }
        single {
            AkkaSystem(koin, Behaviors.supervise(Behaviors.setup {
                PlayerNodeGuardian(it)
            }).onFailure(SupervisorStrategy.resume()))
        }
        single { PlayerSharding(koin) }
        single { PlayerScriptSupport(koin) }
    }
}

fun main(args: Array<String>) {
//    val port = args[0].toUShort()
    PlayerNode().launch()
}
