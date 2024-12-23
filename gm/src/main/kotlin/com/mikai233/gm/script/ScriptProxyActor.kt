package com.mikai233.gm.script

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.core.component.Role
import com.mikai233.common.core.component.config.*
import com.mikai233.common.extension.actorLogger
import com.mikai233.common.extension.runnableAdapter
import com.mikai233.common.extension.shardingEnvelope
import com.mikai233.common.extension.startBroadcastClusterRouterGroup
import com.mikai233.common.inject.XKoin
import com.mikai233.common.message.ExecuteNodeRoleScript
import com.mikai233.common.message.ExecuteNodeScript
import com.mikai233.gm.component.GmSharding
import com.mikai233.shared.message.*
import com.mikai233.common.script.NodeKey
import com.mikai233.common.script.ScriptActor
import com.mikai233.shared.scriptActorServiceKey
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

class ScriptProxyActor(context: ActorContext<ScriptProxyMessage>, private val koin: XKoin) :
    AbstractBehavior<ScriptProxyMessage>(context), KoinComponent by koin {
    private val logger = actorLogger()
    private val runnableAdapter = runnableAdapter { ActorNamedRunnable("scriptProxyActorCoroutine", it::run) }
    private val coroutine = ActorCoroutine(runnableAdapter.safeActorCoroutine())
    private val configCenter by inject<ZookeeperConfigCenter>()
    private val gmSharding by inject<GmSharding>()
    private val playerActorSharding = gmSharding.playerActorSharding
    private val worldActorSharding = gmSharding.worldActorSharding
    private val scriptBroadcastRouter: ActorRef<SerdeScriptMessage>
    private val scriptBroadcastRoleRouter: EnumMap<Role, ActorRef<SerdeScriptMessage>>
    private val scriptTargetNodeRef: MutableMap<NodeKey, ActorRef<SerdeScriptMessage>> = mutableMapOf()
    private val allScriptServiceKey: MutableMap<ServiceKey<SerdeScriptMessage>, Node> = mutableMapOf()

    init {
        scriptBroadcastRouter = spawnScriptBroadcastRouter()
        scriptBroadcastRoleRouter = spawnScriptBroadcastRoleRouter()
        subscribeScriptActor()
    }

    override fun createReceive(): Receive<ScriptProxyMessage> {
        return newReceiveBuilder().onMessage(ScriptProxyMessage::class.java) { message ->
            when (message) {
                is ServiceList -> {
                    handleServiceList(message)
                }

                is ActorNamedRunnable -> {
                    handleScriptProxyActorRunnable(message)
                }

                is DispatchScript -> {
                    handleDispatchScript(message)
                }
            }
            Behaviors.same()
        }.build()
    }

    private fun handleDispatchScript(message: DispatchScript) {
        when (message) {
            is BatchDispatchPlayerActorScript -> {
                handleBatchDispatchPlayerActorScript(message)
            }

            is BatchDispatchWorldActorScript -> {
                handleBatchDispatchWorldActorScript(message)
            }

            is DispatchNodeRoleScript -> {
                handleDispatchNodeRoleScript(message)
            }

            is DispatchNodeScript -> {
                handleDispatchNodeScript(message)
            }
        }
    }

    private fun handleDispatchNodeScript(message: DispatchNodeScript) {
        scriptBroadcastRouter.tell(ExecuteNodeScript(message.script))
    }

    private fun handleDispatchNodeRoleScript(message: DispatchNodeRoleScript) {
        val script = message.script
        val role = message.role
        val scriptActor = scriptBroadcastRoleRouter[role]
        if (scriptActor != null) {
            scriptActor.tell(ExecuteNodeRoleScript(script, role))
        } else {
            logger.warn("script actor of role:{} not found", role)
        }
    }

    //TODO check world exists
    private fun handleBatchDispatchWorldActorScript(message: BatchDispatchWorldActorScript) {
        TODO("Not yet implemented")
    }

    //TODO check player exists
    private fun handleBatchDispatchPlayerActorScript(message: BatchDispatchPlayerActorScript) {
        val script = message.script
        message.playerIds.forEach { playerId ->
            playerActorSharding.tell(shardingEnvelope("$playerId", PlayerScript(script)))
        }
    }

    private fun spawnScriptBroadcastRouter(): ActorRef<SerdeScriptMessage> {
        return context.startBroadcastClusterRouterGroup(setOf(ScriptActor.path()), emptySet())
    }

    private fun spawnScriptBroadcastRoleRouter(): EnumMap<Role, ActorRef<SerdeScriptMessage>> {
        val roleRouters: EnumMap<Role, ActorRef<SerdeScriptMessage>> = EnumMap(Role::class.java)
        Role.values().forEach { role ->
            val router =
                context.startBroadcastClusterRouterGroup<SerdeScriptMessage>(setOf(ScriptActor.path()), setOf(role))
            roleRouters[role] = router
        }
        return roleRouters
    }

    //TODO watch config change
    private fun subscribeScriptActor() {
        with(configCenter) {
            getChildren(serverHostsPath()).forEach { host ->
                getChildren(serverHostPath(host)).forEach { nodePath ->
                    val fullNodePath = nodePath(host, nodePath)
                    val node = getConfigEx<Node>(fullNodePath)
                    val serviceKey = scriptActorServiceKey(node.host, node.port)
                    val command = Receptionist.subscribe(
                        serviceKey,
                        context.messageAdapter(Receptionist.Listing::class.java, ::ServiceList)
                    )
                    allScriptServiceKey[serviceKey] = node
                    context.system.receptionist().tell(command)
                }
            }
        }
    }

    private fun handleServiceList(message: ServiceList) {
        @Suppress("UNCHECKED_CAST") val serviceKey = message.inner.key as ServiceKey<SerdeScriptMessage>
        val node = requireNotNull(allScriptServiceKey[serviceKey]) { "key:${serviceKey} node not found" }
        val serverInstances = message.inner.getServiceInstances(serviceKey)
        if (serverInstances.size > 1) {
            logger.warn("serverInstances of key:{} is not unique", serviceKey)
        }
        val serverInstance = serverInstances.firstOrNull()
        val nodeKey = NodeKey(node.host, node.role, node.port)
        if (serverInstance != null) {
            scriptTargetNodeRef[nodeKey] = serverInstance
            logger.info("script service:{} updated to:{}", nodeKey, serverInstance)
        } else {
            scriptTargetNodeRef.remove(nodeKey)
            logger.warn("script service:{} unreachable", nodeKey)
        }
    }

    private fun handleScriptProxyActorRunnable(message: ActorNamedRunnable): Behavior<ScriptProxyMessage> {
        runCatching(message::run).onFailure {
            logger.error("script proxy actor handle runnable:{} failed", message.name, it)
        }
        return Behaviors.same()
    }
}
