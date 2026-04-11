package com.mikai233.gm.web.dto

import com.mikai233.common.message.ExecuteScriptResult

data class ScriptExecutionResponse(
    val uid: String?,
    val success: Boolean,
    val error: String? = null,
) {
    companion object {
        fun from(result: Result<ExecuteScriptResult>): ScriptExecutionResponse {
            return result.fold(
                onSuccess = { ScriptExecutionResponse(it.uid, it.success) },
                onFailure = { ScriptExecutionResponse(null, false, it.message ?: it.javaClass.simpleName) },
            )
        }
    }
}
