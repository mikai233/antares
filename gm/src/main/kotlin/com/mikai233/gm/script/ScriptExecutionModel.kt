package com.mikai233.gm.script

import com.mikai233.common.core.Role
import com.mikai233.common.message.Message
import com.mikai233.common.script.Script
import org.apache.pekko.actor.Address
import java.time.Instant

enum class ScriptExecutionTargetType {
    PlayerActor,
    WorldActor,
    GlobalActor,
    ActorPath,
    Node,
    NodeRole,
}

enum class ScriptExecutionStatus {
    Running,
    Completed,
    PartialFailed,
    Failed,
    Timeout,
}

enum class ScriptExecutionTargetStatus {
    Running,
    Success,
    Failed,
    Timeout,
}

data class ScriptExecutionTargetView(
    val target: String,
    val status: ScriptExecutionTargetStatus,
    val success: Boolean? = null,
    val error: String? = null,
    val nodeAddress: String? = null,
    val actorPath: String? = null,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
)

data class ScriptExecutionView(
    val id: String,
    val scriptName: String,
    val scriptType: String,
    val targetType: ScriptExecutionTargetType,
    val status: ScriptExecutionStatus,
    val totalTargets: Int,
    val successCount: Int,
    val failureCount: Int,
    val timeoutCount: Int,
    val createdAt: Instant,
    val finishedAt: Instant? = null,
    val targets: List<ScriptExecutionTargetView>,
) : Message

data class ScriptExecutionListView(val executions: List<ScriptExecutionView>) : Message

data class StartScriptExecution(
    val id: String,
    val script: Script,
    val targetType: ScriptExecutionTargetType,
    val targets: Set<String> = emptySet(),
    val addressFilter: Set<Address> = emptySet(),
    val role: Role? = null,
) : Message

data class GetScriptExecution(val id: String) : Message

data object ListScriptExecutions : Message

data class ScriptExecutionNotFound(val id: String) : Message
