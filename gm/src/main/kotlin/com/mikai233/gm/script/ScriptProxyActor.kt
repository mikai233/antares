package com.mikai233.gm.script

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.core.actor.safeActorCoroutine
import com.mikai233.common.core.components.Role
import com.mikai233.common.core.components.ZookeeperConfigCenter
import com.mikai233.common.core.components.config.*
import com.mikai233.common.ext.actorLogger
import com.mikai233.common.ext.runnableAdapter
import com.mikai233.common.ext.shardingEnvelope
import com.mikai233.common.ext.startBroadcastClusterRouterGroup
import com.mikai233.gm.GmNode
import com.mikai233.shared.message.*
import com.mikai233.shared.script.NodeKey
import com.mikai233.shared.script.ScriptActor
import com.mikai233.shared.scriptActorServiceKey
import java.util.*

class ScriptProxyActor(context: ActorContext<ScriptProxyMessage>, private val gmNode: GmNode) :
    AbstractBehavior<ScriptProxyMessage>(context) {
    private val logger = actorLogger()
    private val runnableAdapter = runnableAdapter { ScriptRunnable(it::run) }
    private val coroutine = ActorCoroutine(runnableAdapter.safeActorCoroutine())
    private val configCenter = gmNode.server.component<ZookeeperConfigCenter>()
    private val playerActor = gmNode.playerActor()
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
                is ServiceList -> handleServiceList(message)
                is ScriptRunnable -> message.run()
                is DispatchScript -> handleDispatchScript(message)
                is ExecuteScriptResult -> return@onMessage Behaviors.unhandled()
            }
            Behaviors.same()
        }.build()
    }

    private fun handleDispatchScript(message: DispatchScript) {
        when (message) {
            is BatchDispatchPlayerActorScript -> handleBatchDispatchPlayerActorScript(message)
            is BatchDispatchWorldActorScript -> handleBatchDispatchWorldActorScript(message)
            is DispatchNodeRoleScript -> handleDispatchNodeRoleScript(message)
            is DispatchNodeScript -> handleDispatchNodeScript(message)
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
            playerActor.tell(shardingEnvelope("$playerId", PlayerScript(script)))
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
}