package com.mikai233.gm

import akka.actor.typed.ActorRef
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.Server
import com.mikai233.common.core.State
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.core.component.NodeConfigHolder
import com.mikai233.common.core.component.Role
import com.mikai233.common.core.component.ZookeeperConfigCenter
import com.mikai233.common.ext.*
import com.mikai233.common.inject.XKoin
import com.mikai233.gm.component.GmScriptSupport
import com.mikai233.gm.component.GmSharding
import com.mikai233.gm.script.ScriptProxyActor
import com.mikai233.shared.component.ExcelConfigHolder
import com.mikai233.shared.message.ScriptProxyMessage
import com.mikai233.shared.message.SerdeScriptMessage
import com.mikai233.shared.script.ScriptActor
import com.mikai233.shared.scriptActorServiceKey
import org.koin.core.component.get
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.logger.slf4jLogger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class GmNode(private val port: Int = 2339, private val sameJvm: Boolean = false) : Launcher {
    lateinit var koin: XKoin
        private set

    inner class GmNodeGuardian(context: ActorContext<GmSystemMessage>) :
        AbstractBehavior<GmSystemMessage>(context) {
        private val logger = actorLogger()
        private val scriptProxyActor: ActorRef<ScriptProxyMessage>

        init {
            scriptProxyActor = startScriptProxyActor()
        }

        override fun createReceive(): Receive<GmSystemMessage> {
            return newReceiveBuilder().onMessage(GmSystemMessage::class.java) { message ->
                when (message) {
                    is SpawnScriptActorReq -> handleSpawnScriptActorReq(message)
                    is SpawnScriptRouterReq -> handleSpawnScriptRouterReq(message)
                }
                Behaviors.same()
            }.build()
        }

        private fun handleSpawnScriptActorReq(message: SpawnScriptActorReq) {
            val scriptActor = context.spawn(Behaviors.setup { ScriptActor(it, this@GmNode) }, ScriptActor.name())
            context.system.registerService(
                scriptActorServiceKey(GlobalEnv.machineIp, port),
                scriptActor.narrow()
            )
            message.replyTo.tell(SpawnScriptActorResp(scriptActor))
        }

        private fun handleSpawnScriptRouterReq(message: SpawnScriptRouterReq) {
            val broadcastRouter =
                context.startBroadcastClusterRouterGroup<SerdeScriptMessage>(setOf(ScriptActor.path()), emptySet())
            message.replyTo.tell(SpawnScriptRouterResp(broadcastRouter))
        }

        private fun startScriptProxyActor(): ActorRef<ScriptProxyMessage> {
            val behavior = Behaviors.supervise(Behaviors.setup { ScriptProxyActor(it, koin) }).onFailure(
                SupervisorStrategy.restartWithBackoff(
                    1.seconds.toJavaDuration(),
                    10.seconds.toJavaDuration(),
                    0.5
                )
            )
            return context.system.startSingleton(behavior, "ScriptProxyActor", Role.Gm)
        }
    }

    init {
        koinApplication {
            this@GmNode.koin = XKoin(this)
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
        single { this@GmNode }
        single { Server(koin) }
        closeableSingle { ZookeeperConfigCenter() }
        single { NodeConfigHolder(koin, Role.Gm, port, sameJvm) }
        single { ExcelConfigHolder(koin) }
        single {
            AkkaSystem(koin, Behaviors.supervise(Behaviors.setup {
                GmNodeGuardian(it)
            }).onFailure(SupervisorStrategy.resume()))
        }
        single { GmSharding(koin) }
        single { GmScriptSupport(koin) }
    }
}

fun main() {
    GmNode().launch()
}
