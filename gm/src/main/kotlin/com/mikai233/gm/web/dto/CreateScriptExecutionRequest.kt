package com.mikai233.gm.web.dto

import com.mikai233.gm.script.ScriptExecutionTargetType

data class CreateScriptExecutionRequest(
    val targetType: ScriptExecutionTargetType,
    val targets: List<String> = emptyList(),
    val role: String? = null,
    val addresses: List<String> = emptyList(),
    val patch: Boolean = false,
)
