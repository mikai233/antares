package com.mikai233.shared.message

import akka.actor.typed.receptionist.Receptionist
import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.core.components.Role
import com.mikai233.common.msg.Message
import com.mikai233.common.msg.SerdeMessage
import com.mikai233.shared.script.Script

sealed interface ScriptProxyMessage : Message

sealed interface SerdeScriptProxyMessage : ScriptProxyMessage, SerdeMessage

@AllOpen
sealed class ExecuteScriptResult(val success: Boolean, val throwable: Throwable?) : SerdeScriptProxyMessage

data class ServiceList(val inner: Receptionist.Listing) : ScriptProxyMessage

data class ScriptRunnable(private val block: () -> Unit) : Runnable, ScriptProxyMessage {
    override fun run() {
        block()
    }
}

@AllOpen
sealed class DispatchScript : ScriptProxyMessage

data class DispatchNodeScript(val script: Script) : DispatchScript()

data class DispatchNodeRoleScript(val script: Script, val role: Role) : DispatchScript()

data class BatchDispatchPlayerActorScript(val script: Script, val playerIds: List<Long>) : DispatchScript()

data class BatchDispatchWorldActorScript(val script: Script, val worldIds: List<Long>) : DispatchScript()
