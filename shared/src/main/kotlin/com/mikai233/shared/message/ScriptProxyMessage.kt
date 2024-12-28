package com.mikai233.shared.message

import com.mikai233.common.core.Role
import com.mikai233.common.message.Message
import com.mikai233.common.script.Script

sealed interface ScriptProxyMessage : Message

interface DispatchScript : ScriptProxyMessage {
    val script: Script
}

data class DispatchNodeScript(override val script: Script) : DispatchScript

data class DispatchNodeRoleScript(override val script: Script, val role: Role) : DispatchScript

data class BatchDispatchPlayerActorScript(override val script: Script, val playerIds: List<Long>) : DispatchScript

data class BatchDispatchWorldActorScript(override val script: Script, val worldIds: List<Long>) : DispatchScript
