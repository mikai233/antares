package com.mikai233.shared.script

import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.cluster.Member
import akka.cluster.typed.Cluster
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.ext.actorLogger
import com.mikai233.shared.message.NodeRoleScript
import com.mikai233.shared.message.NodeScript
import com.mikai233.shared.message.ScriptMessage

class ScriptActor(context: ActorContext<ScriptMessage>) : AbstractBehavior<ScriptMessage>(context) {
    private val logger = actorLogger()
    private val selfMember: Member

    companion object {
        fun name() = "scriptActor@${GlobalEnv.machineIp}"
        fun path() = "/user/${name()}"
    }

    init {
        logger.info("{} started", context.self)
        selfMember = Cluster.get(context.system).selfMember()
    }

    override fun createReceive(): Receive<ScriptMessage> {
        return newReceiveBuilder().onMessage(ScriptMessage::class.java) { message ->
            when (message) {
                is NodeRoleScript -> handleNodeRoleScript(message)
                is NodeScript -> handleNodeScript(message)
            }
            Behaviors.same()
        }.build()
    }

    private fun handleNodeRoleScript(message: NodeRoleScript) {
        val targetRole = message.role.name
        if (selfMember.hasRole(targetRole)) {

        } else {
            logger.error(
                "incorrect role script:{} route to member:{} with role:{}",
                targetRole,
                selfMember.address(),
                selfMember.roles
            )
        }
    }

    private fun handleNodeScript(message: NodeScript) {

    }
}