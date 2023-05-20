package com.mikai233.shared.message

import akka.actor.typed.ActorRef
import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.core.components.Role
import com.mikai233.shared.script.Script

@AllOpen
sealed class ScriptWrap(val script: Script)

data class ExecuteNodeRoleScript(override val script: Script, val role: Role) : ScriptWrap(script), SerdeScriptMessage

data class ExecuteNodeScript(override val script: Script) : ScriptWrap(script), SerdeScriptMessage

data class CompilePlayerActorScript(override val script: Script, val replyTo: ActorRef<PlayerMessage>) :
    ScriptWrap(script), ScriptMessage

data class CompileWorldActorScript(override val script: Script, val replyTo: ActorRef<WorldMessage>) :
    ScriptWrap(script), ScriptMessage