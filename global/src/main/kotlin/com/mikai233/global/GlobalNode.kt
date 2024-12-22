package com.mikai233.global

import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.Node
import com.mikai233.common.core.State
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.core.component.NodeConfigHolder
import com.mikai233.common.core.component.Role
import com.mikai233.common.core.component.ZookeeperConfigCenter
import com.mikai233.common.extension.closeableSingle
import com.mikai233.common.extension.registerService
import com.mikai233.common.inject.XKoin
import com.mikai233.global.component.GlobalActorDispatcher
import com.mikai233.global.component.GlobalScriptSupport
import com.mikai233.global.component.GlobalSharding
import com.mikai233.shared.component.ExcelConfigHolder
import com.mikai233.shared.script.ScriptActor
import com.mikai233.shared.scriptActorServiceKey
import org.koin.core.component.get
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.logger.slf4jLogger

class GlobalNode(private val port: Int = 2338, private val sameJvm: Boolean = false) : Launcher {
    lateinit var koin: XKoin
        private set

    inner class PlayerNodeGuardian(context: ActorContext<GlobalSystemMessage>) :
        AbstractBehavior<GlobalSystemMessage>(context) {

        override fun createReceive(): Receive<GlobalSystemMessage> {
            return newReceiveBuilder().onMessage(GlobalSystemMessage::class.java) { message ->
                when (message) {
                    is SpawnScriptActorReq -> handleSpawnScriptActorReq(message)
                }
                Behaviors.same()
            }.build()
        }

        private fun handleSpawnScriptActorReq(message: SpawnScriptActorReq) {
            val scriptActor = context.spawn(Behaviors.setup { ScriptActor(it, this@GlobalNode) }, ScriptActor.name())
            context.system.registerService(
                scriptActorServiceKey(GlobalEnv.machineIp, port),
                scriptActor.narrow()
            )
            message.replyTo.tell(SpawnScriptActorResp(scriptActor))
        }
    }

    init {
        koinApplication {
            this@GlobalNode.koin = XKoin(this)
            slf4jLogger()
            modules(serverModule())
        }
    }

    override fun launch() {
        val node = koin.get<Node>()
        node.state = State.Initializing
        node.onInit()
        node.state = State.Running
    }

    private fun serverModule() = module(createdAtStart = true) {
        single { this@GlobalNode }
        single { Node(koin) }
        single { GlobalActorDispatcher(koin) }
        closeableSingle { ZookeeperConfigCenter() }
        single { NodeConfigHolder(koin, Role.Global, port, sameJvm) }
        single { ExcelConfigHolder(koin) }
        single {
            AkkaSystem(koin, Behaviors.supervise(Behaviors.setup {
                PlayerNodeGuardian(it)
            }).onFailure(SupervisorStrategy.resume()))
        }
        single { GlobalSharding(koin) }
        single { GlobalScriptSupport(koin) }
    }
}

fun main(args: Array<String>) {
    val port = args[0].toInt()
    GlobalNode(port = port).launch()
}
