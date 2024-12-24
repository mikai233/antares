package com.mikai233.common.message

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.core.Role
import com.mikai233.common.script.Script

sealed interface ScriptMessage

@AllOpen
sealed class ScriptWrap(val script: Script)

data class ExecuteNodeRoleScript(override val script: Script, val role: Role) : ScriptWrap(script), ScriptMessage

data class ExecuteNodeScript(override val script: Script) : ScriptWrap(script), ScriptMessage

data class CompileScript(override val script: Script) : ScriptWrap(script), ScriptMessage
