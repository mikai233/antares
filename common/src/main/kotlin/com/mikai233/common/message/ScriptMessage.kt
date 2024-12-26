package com.mikai233.common.message

import akka.actor.AbstractActor
import com.mikai233.common.core.Role
import com.mikai233.common.script.ActorScriptFunction
import com.mikai233.common.script.Script

sealed interface ScriptMessage

data class ExecuteNodeRoleScript(val script: Script, val role: Role) : ScriptMessage

data class ExecuteNodeScript(val script: Script) : ScriptMessage

data class ExecuteActorScript(val script: Script) : ScriptMessage

data class ExecuteActorFunction(val function: ActorScriptFunction<in AbstractActor>) : ScriptMessage
