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
import com.mikai233.common.extension.closeableSingle
import com.mikai233.common.extension.registerService
import com.mikai233.common.inject.XKoin
import com.mikai233.protocol.MsgCs
import com.mikai233.protocol.MsgSc
import com.mikai233.shared.component.ExcelConfigHolder
import com.mikai233.shared.script.ScriptActor
import com.mikai233.shared.scriptActorServiceKey
import com.mikai233.world.component.WorldMessageDispatcher
import com.mikai233.world.component.WorldScriptSupport
import com.mikai233.world.component.WorldSharding
import com.mikai233.world.component.WorldWaker
import org.koin.core.component.get
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.logger.slf4jLogger

class WorldNode(private val port: Int = 2336, private val sameJvm: Boolean = false) : Launcher {
    lateinit var koin: XKoin
        private set

    inner class WorldNodeGuardian(context: ActorContext<WorldSystemMessage>) :
        AbstractBehavior<WorldSystemMessage>(context) {

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
        koinApplication {
            this@WorldNode.koin = XKoin(this)
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
        single { this@WorldNode }
        single { Server(koin) }
        single { WorldMessageDispatcher(koin) }
        closeableSingle { ZookeeperConfigCenter() }
        closeableSingle { MongoHolder(koin) }
        single { NodeConfigHolder(koin, Role.World, port, sameJvm) }
        single { ExcelConfigHolder(koin) }
        single {
            AkkaSystem(koin, Behaviors.supervise(Behaviors.setup {
                WorldNodeGuardian(it)
            }).onFailure(SupervisorStrategy.resume()))
        }
        single { WorldSharding(koin) }
        single { WorldScriptSupport(koin) }
        single { WorldConfigHolder(koin) }
        single { WorldWaker(koin) }
    }
}

fun main(args: Array<String>) {
    val port = args[0].toInt()
    WorldNode(port = port).launch()
}
