package com.mikai233.gm.script

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "gm_script_execution")
data class ScriptExecutionDocument(
    @Id
    val id: String = "",
    val scriptName: String = "",
    val scriptType: String = "",
    val targetType: ScriptExecutionTargetType = ScriptExecutionTargetType.Node,
    val status: ScriptExecutionStatus = ScriptExecutionStatus.Running,
    val totalTargets: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val timeoutCount: Int = 0,
    @Indexed
    val createdAt: Instant = Instant.EPOCH,
    val finishedAt: Instant? = null,
    val dynamicTargets: Boolean = false,
)

@Document(collection = "gm_script_execution_target")
data class ScriptExecutionTargetDocument(
    @Id
    val id: String = "",
    @Indexed
    val executionId: String = "",
    val target: String = "",
    val status: ScriptExecutionTargetStatus = ScriptExecutionTargetStatus.Running,
    val success: Boolean? = null,
    val error: String? = null,
    val nodeAddress: String? = null,
    val actorPath: String? = null,
    val startedAt: Instant = Instant.EPOCH,
    val finishedAt: Instant? = null,
)

fun ScriptExecutionView.toDocument(dynamicTargets: Boolean): ScriptExecutionDocument {
    return ScriptExecutionDocument(
        id,
        scriptName,
        scriptType,
        targetType,
        status,
        totalTargets,
        successCount,
        failureCount,
        timeoutCount,
        createdAt,
        finishedAt,
        dynamicTargets,
    )
}

fun ScriptExecutionTargetView.toDocument(executionId: String): ScriptExecutionTargetDocument {
    return ScriptExecutionTargetDocument(
        "$executionId:$target",
        executionId,
        target,
        status,
        success,
        error,
        nodeAddress,
        actorPath,
        startedAt,
        finishedAt,
    )
}

fun ScriptExecutionDocument.toView(targets: List<ScriptExecutionTargetView> = emptyList()): ScriptExecutionView {
    return ScriptExecutionView(
        id,
        scriptName,
        scriptType,
        targetType,
        status,
        totalTargets,
        successCount,
        failureCount,
        timeoutCount,
        createdAt,
        finishedAt,
        targets,
    )
}

fun ScriptExecutionTargetDocument.toView(): ScriptExecutionTargetView {
    return ScriptExecutionTargetView(
        target,
        status,
        success,
        error,
        nodeAddress,
        actorPath,
        startedAt,
        finishedAt,
    )
}
