package com.mikai233.gm.script

import akka.actor.ActorRef
import com.mikai233.common.core.Role
import com.mikai233.common.core.actor.StatefulActor
import com.mikai233.gm.GmNode
import com.mikai233.shared.message.*
import java.util.*

class ScriptProxyActor(node: GmNode) : StatefulActor<GmNode>(node) {

    override fun createReceive(): Receive {
        return receiveBuilder().build()
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

    }

    private fun handleDispatchNodeRoleScript(message: DispatchNodeRoleScript) {
        val script = message.script
        val role = message.role

    }

    //TODO check world exists
    private fun handleBatchDispatchWorldActorScript(message: BatchDispatchWorldActorScript) {
        TODO("Not yet implemented")
    }

    //TODO check player exists
    private fun handleBatchDispatchPlayerActorScript(message: BatchDispatchPlayerActorScript) {
        val script = message.script
    }

    private fun spawnScriptBroadcastRouter(): ActorRef {
        TODO()
    }

    private fun spawnScriptBroadcastRoleRouter(): EnumMap<Role, ActorRef> {
        TODO()
    }

    //TODO watch config change
    private fun subscribeScriptActor() {

    }
}
