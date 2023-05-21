package com.mikai233.gm

import akka.actor.typed.ActorRef
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.cluster.typed.ClusterSingleton
import akka.cluster.typed.ClusterSingletonSettings
import akka.cluster.typed.SingletonActor
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.Server
import com.mikai233.common.core.State
import com.mikai233.common.core.components.AkkaSystem
import com.mikai233.common.core.components.NodeConfigsComponent
import com.mikai233.common.core.components.Role
import com.mikai233.common.core.components.ZookeeperConfigCenter
import com.mikai233.common.ext.actorLogger
import com.mikai233.common.ext.registerService
import com.mikai233.common.ext.startBroadcastClusterRouterGroup
import com.mikai233.gm.component.ScriptSupport
import com.mikai233.gm.component.Sharding
import com.mikai233.gm.script.ScriptProxyActor
import com.mikai233.shared.message.ScriptProxyMessage
import com.mikai233.shared.message.SerdeScriptMessage
import com.mikai233.shared.script.ScriptActor
import com.mikai233.shared.scriptActorServiceKey
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class GmNode(private val port: Int = 2339, private val sameJvm: Boolean = false) : Launcher {
    val server: Server = Server()

    inner class GmNodeGuardian(context: ActorContext<GmSystemMessage>) :
        AbstractBehavior<GmSystemMessage>(context) {
        private val logger = actorLogger()
        private val scriptProxyActor: ActorRef<ScriptProxyMessage>
        private val singleton = ClusterSingleton.get(context.system)

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
            val singletonSettings = ClusterSingletonSettings.create(context.system).withRole(Role.Gm.name)
            val behavior = Behaviors.supervise(Behaviors.setup { ScriptProxyActor(it, this@GmNode) }).onFailure(
                SupervisorStrategy.restartWithBackoff(
                    1.seconds.toJavaDuration(),
                    10.seconds.toJavaDuration(),
                    0.5
                )
            )
            val scriptProxyActor = SingletonActor.of(behavior, "ScriptProxyActor").withSettings(singletonSettings)
            return singleton.init(scriptProxyActor)
        }
    }

    init {
        server.components {
            component {
                ZookeeperConfigCenter()
            }
            component {
                NodeConfigsComponent(this, Role.Gm, port, sameJvm)
            }
            component {
                AkkaSystem(this, Behaviors.supervise(Behaviors.setup {
                    GmNodeGuardian(it)
                }).onFailure(SupervisorStrategy.resume()))
            }
            component {
                Sharding(this)
            }
            component {
                ScriptSupport(this)
            }
        }
    }

    fun system() = server.component<AkkaSystem<GmSystemMessage>>().system

    fun playerActor() = server.component<Sharding>().playerActor

    override fun launch() {
        server.state = State.Initializing
        server.initComponents()
        server.state = State.Running
    }
}

fun main() {
    GmNode().launch()
}