package com.mikai233.gm.script

import com.mikai233.common.core.Singleton
import com.mikai233.common.extension.actorLogger
import com.mikai233.common.message.ExecuteActorScript
import com.mikai233.common.message.ExecuteNodeRoleScript
import com.mikai233.common.message.ExecuteNodeScript
import com.mikai233.common.message.ExecuteScriptResult
import com.mikai233.gm.GmNode
import org.apache.pekko.actor.AbstractActor
import org.apache.pekko.actor.Props
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class ScriptExecutionManagerActor(private val node: GmNode) : AbstractActor() {
    companion object {
        const val NAME = "scriptExecutionManager"
        private const val MAX_EXECUTION_RECORDS = 1_000

        fun props(node: GmNode): Props {
            return Props.create(ScriptExecutionManagerActor::class.java) { ScriptExecutionManagerActor(node) }
        }
    }

    private data class MutableExecution(
        val id: String,
        val scriptName: String,
        val scriptType: String,
        val targetType: ScriptExecutionTargetType,
        val targets: MutableMap<String, MutableTarget>,
        val createdAt: Instant,
        val dynamicTargets: Boolean,
        var status: ScriptExecutionStatus = ScriptExecutionStatus.Running,
        var finishedAt: Instant? = null,
    )

    private data class MutableTarget(
        val target: String,
        var status: ScriptExecutionTargetStatus,
        var success: Boolean? = null,
        var error: String? = null,
        var nodeAddress: String? = null,
        var actorPath: String? = null,
        val startedAt: Instant,
        var finishedAt: Instant? = null,
    )

    private data class ScriptExecutionTimeout(val id: String)

    private val logger = actorLogger()
    private val repository = ScriptExecutionRepository { node.mongoDB.mongoTemplate }
    private val executions = object : LinkedHashMap<String, MutableExecution>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableExecution>?): Boolean {
            return size > MAX_EXECUTION_RECORDS
        }
    }

    override fun preStart() {
        loadRecentExecutions()
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(StartScriptExecution::class.java) { handleStart(it) }
            .match(ExecuteScriptResult::class.java) { handleResult(it) }
            .match(GetScriptExecution::class.java) { handleGet(it) }
            .match(ListScriptExecutions::class.java) { handleList() }
            .match(ScriptExecutionTimeout::class.java) { handleTimeout(it) }
            .build()
    }

    private fun handleStart(command: StartScriptExecution) {
        val execution = MutableExecution(
            command.id,
            command.script.name,
            command.script.type.name,
            command.targetType,
            command.targets.associateWith {
                MutableTarget(it, ScriptExecutionTargetStatus.Running, startedAt = Instant.now())
            }.toMutableMap(),
            Instant.now(),
            command.targetType in setOf(ScriptExecutionTargetType.Node, ScriptExecutionTargetType.NodeRole) &&
                command.targets.isEmpty(),
        )
        executions.put(command.id, execution)
        persist(execution, persistTargets = true)
        scheduleTimeout(command.id)
        dispatch(command)
        sender.tell(execution.toView(), self)
    }

    private fun dispatch(command: StartScriptExecution) {
        when (command.targetType) {
            ScriptExecutionTargetType.PlayerActor -> {
                command.targets.forEach { target ->
                    node.playerSharding.tell(
                        ExecuteActorScript(target.toLong(), command.id, command.script, target),
                        self,
                    )
                }
            }

            ScriptExecutionTargetType.WorldActor -> {
                command.targets.forEach { target ->
                    node.worldSharding.tell(
                        ExecuteActorScript(target.toLong(), command.id, command.script, target),
                        self,
                    )
                }
            }

            ScriptExecutionTargetType.GlobalActor -> {
                val target = command.targets.single()
                val singleton = Singleton.fromActorName(target)
                if (singleton == Singleton.Worker) {
                    node.workerSingletonProxy.tell(ExecuteActorScript(0, command.id, command.script, target), self)
                } else {
                    markFailed(
                        command.id,
                        target,
                        "Unsupported singleton actor: $target",
                    )
                }
            }

            ScriptExecutionTargetType.ActorPath -> {
                command.targets.forEach { target ->
                    node.system.actorSelection(target).tell(
                        ExecuteActorScript(0, command.id, command.script, target),
                        self,
                    )
                }
            }

            ScriptExecutionTargetType.Node -> {
                node.scriptRouter.tell(ExecuteNodeScript(command.id, command.script, command.addressFilter), self)
            }

            ScriptExecutionTargetType.NodeRole -> {
                val role = requireNotNull(command.role) { "NodeRole execution requires role" }
                node.scriptRouter.tell(
                    ExecuteNodeRoleScript(command.id, command.script, role, command.addressFilter),
                    self,
                )
            }
        }
    }

    private fun handleResult(result: ExecuteScriptResult) {
        val execution = executions[result.uid] ?: return
        val target = result.target ?: result.actorPath ?: result.nodeAddress ?: sender.path().toString()
        val targetRecord = execution.targets.getOrPut(target) {
            MutableTarget(target, ScriptExecutionTargetStatus.Running, startedAt = Instant.now())
        }
        targetRecord.status = if (result.success) {
            ScriptExecutionTargetStatus.Success
        } else {
            ScriptExecutionTargetStatus.Failed
        }
        targetRecord.success = result.success
        targetRecord.error = result.error
        targetRecord.nodeAddress = result.nodeAddress
        targetRecord.actorPath = result.actorPath
        targetRecord.finishedAt = Instant.now()
        refreshExecutionStatus(execution)
        persist(execution)
        persistTarget(execution.id, targetRecord)
    }

    private fun handleGet(query: GetScriptExecution) {
        val cachedExecution = executions[query.id]
        val execution = runCatching {
            repository.findById(query.id)?.toView(
                repository.findTargets(query.id).map { it.toView() },
            )
        }
            .onFailure { logger.error(it, "load script execution:{} failed", query.id) }
            .getOrNull()
            ?: cachedExecution?.toView()
        sender.tell(execution ?: ScriptExecutionNotFound(query.id), self)
    }

    private fun handleList() {
        val records = runCatching {
            repository.findRecent(MAX_EXECUTION_RECORDS).map { it.toView() }
        }.onFailure {
            logger.error(it, "list script executions from db failed")
        }.getOrElse {
            executions.values
                .map { it.toView() }
                .sortedByDescending { it.createdAt }
        }
        sender.tell(ScriptExecutionListView(records), self)
    }

    private fun handleTimeout(timeout: ScriptExecutionTimeout) {
        val execution = executions[timeout.id] ?: return
        if (execution.finishedAt != null) {
            return
        }
        execution.targets.values
            .filter { it.status == ScriptExecutionTargetStatus.Running }
            .forEach {
                it.status = ScriptExecutionTargetStatus.Timeout
                it.success = false
                it.error = "Script execution timed out"
                it.finishedAt = Instant.now()
            }
        if (execution.targets.isEmpty()) {
            execution.status = ScriptExecutionStatus.Timeout
            execution.finishedAt = Instant.now()
        } else {
            refreshExecutionStatus(execution, forceFinish = true)
        }
        persist(execution, persistTargets = true)
        logger.warning("script execution:{} timed out", timeout.id)
    }

    private fun markFailed(id: String, target: String, error: String) {
        val execution = executions[id] ?: return
        val targetRecord = execution.targets.getOrPut(target) {
            MutableTarget(target, ScriptExecutionTargetStatus.Running, startedAt = Instant.now())
        }
        targetRecord.status = ScriptExecutionTargetStatus.Failed
        targetRecord.success = false
        targetRecord.error = error
        targetRecord.finishedAt = Instant.now()
        refreshExecutionStatus(execution)
        persist(execution)
        persistTarget(execution.id, targetRecord)
    }

    private fun refreshExecutionStatus(execution: MutableExecution, forceFinish: Boolean = false) {
        if (execution.dynamicTargets && !forceFinish) {
            return
        }
        if (execution.targets.isEmpty()) {
            return
        }
        val allFinished = execution.targets.values.all {
            it.status != ScriptExecutionTargetStatus.Running
        }
        if (!allFinished && !forceFinish) {
            return
        }
        val successCount = execution.targets.values.count { it.status == ScriptExecutionTargetStatus.Success }
        val timeoutCount = execution.targets.values.count { it.status == ScriptExecutionTargetStatus.Timeout }
        val failureCount = execution.targets.values.count { it.status == ScriptExecutionTargetStatus.Failed }
        execution.status = when {
            timeoutCount == execution.targets.size -> ScriptExecutionStatus.Timeout
            successCount == execution.targets.size -> ScriptExecutionStatus.Completed
            failureCount == execution.targets.size -> ScriptExecutionStatus.Failed
            else -> ScriptExecutionStatus.PartialFailed
        }
        execution.finishedAt = Instant.now()
    }

    private fun scheduleTimeout(id: String) {
        context.system.scheduler().scheduleOnce(
            3.minutes.toJavaDuration(),
            self,
            ScriptExecutionTimeout(id),
            context.dispatcher,
            self,
        )
    }

    private fun loadRecentExecutions() {
        runCatching {
            val documents = repository.findRecent(MAX_EXECUTION_RECORDS)
            val runningTargets = repository.findTargets(
                documents
                    .filter { it.status == ScriptExecutionStatus.Running }
                    .map { it.id },
            )
            documents
                .asReversed()
                .forEach { document ->
                    val execution = document.toMutableExecution(runningTargets[document.id].orEmpty())
                    if (execution.status == ScriptExecutionStatus.Running) {
                        markRestoredRunningExecutionTimeout(execution)
                    }
                    executions[execution.id] = execution
                }
        }.onFailure {
            logger.error(it, "load script executions from db failed")
        }
    }

    private fun markRestoredRunningExecutionTimeout(execution: MutableExecution) {
        val now = Instant.now()
        execution.targets.values
            .filter { it.status == ScriptExecutionTargetStatus.Running }
            .forEach {
                it.status = ScriptExecutionTargetStatus.Timeout
                it.success = false
                it.error = "GM restarted before script execution finished"
                it.finishedAt = now
            }
        if (execution.targets.isEmpty()) {
            execution.status = ScriptExecutionStatus.Timeout
            execution.finishedAt = now
        } else {
            refreshExecutionStatus(execution, forceFinish = true)
        }
        persist(execution, persistTargets = true)
    }

    private fun persist(execution: MutableExecution, persistTargets: Boolean = false) {
        runCatching {
            val view = execution.toView()
            repository.saveExecution(view, execution.dynamicTargets)
            if (persistTargets) {
                repository.saveTargets(execution.id, view.targets)
            }
        }.onFailure {
            logger.error(it, "save script execution:{} failed", execution.id)
        }
    }

    private fun persistTarget(executionId: String, target: MutableTarget) {
        runCatching {
            repository.saveTarget(executionId, target.toView())
        }.onFailure {
            logger.error(it, "save script execution:{} target:{} failed", executionId, target.target)
        }
    }

    private fun MutableExecution.toView(): ScriptExecutionView {
        val targetViews = targets.values.map { it.toView() }.sortedBy { it.target }
        return ScriptExecutionView(
            id,
            scriptName,
            scriptType,
            targetType,
            status,
            targetViews.size,
            targetViews.count { it.status == ScriptExecutionTargetStatus.Success },
            targetViews.count { it.status == ScriptExecutionTargetStatus.Failed },
            targetViews.count { it.status == ScriptExecutionTargetStatus.Timeout },
            createdAt,
            finishedAt,
            targetViews,
        )
    }

    private fun ScriptExecutionDocument.toMutableExecution(
        targetDocuments: List<ScriptExecutionTargetDocument>,
    ): MutableExecution {
        return MutableExecution(
            id,
            scriptName,
            scriptType,
            targetType,
            targetDocuments.associate { it.target to it.toMutableTarget() }.toMutableMap(),
            createdAt,
            dynamicTargets,
            status,
            finishedAt,
        )
    }

    private fun MutableTarget.toView(): ScriptExecutionTargetView {
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

    private fun ScriptExecutionTargetDocument.toMutableTarget(): MutableTarget {
        return MutableTarget(
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

}
