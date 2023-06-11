package com.mikai233.shared.message

import akka.actor.typed.receptionist.Receptionist
import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.core.component.Role
import com.mikai233.common.msg.Message
import com.mikai233.common.msg.SerdeMessage
import com.mikai233.shared.script.Script

sealed interface ScriptProxyMessage : Message

sealed interface SerdeScriptProxyMessage : ScriptProxyMessage, SerdeMessage

data class ServiceList(val inner: Receptionist.Listing) : ScriptProxyMessage

@AllOpen
sealed class DispatchScript : ScriptProxyMessage

data class DispatchNodeScript(val script: Script) : DispatchScript()

data class DispatchNodeRoleScript(val script: Script, val role: Role) : DispatchScript()

data class BatchDispatchPlayerActorScript(val script: Script, val playerIds: List<Long>) : DispatchScript()

data class BatchDispatchWorldActorScript(val script: Script, val worldIds: List<Long>) : DispatchScript()
