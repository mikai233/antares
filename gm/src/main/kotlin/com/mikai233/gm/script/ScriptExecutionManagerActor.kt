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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class ScriptExecutionManagerActor(private val node: GmNode) : AbstractActor() {
    companion object {
        const val NAME = "scriptExecutionManager"
        private const val MAX_EXECUTION_RECORDS = 1_000
        private const val MAX_DIRTY_TARGETS = 200
        private val PERSISTENCE_FLUSH_INTERVAL = 500.milliseconds

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
        var totalTargets: Int,
        var runningCount: Int,
        var successCount: Int = 0,
        var failureCount: Int = 0,
        var timeoutCount: Int = 0,
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

    private data class DirtyTarget(val executionId: String, val target: ScriptExecutionTargetView)

    private data class ScriptExecutionTimeout(val id: String)

    private data object FlushScriptExecutionPersistence

    private val logger = actorLogger()
    private val repository = ScriptExecutionRepository { node.mongoDB.mongoTemplate }
    private val dirtyExecutions = linkedSetOf<String>()
    private val dirtyTargets = linkedMapOf<String, DirtyTarget>()
    private var persistenceFlushScheduled = false
    private val executions = LinkedHashMap<String, MutableExecution>()

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
            .match(FlushScriptExecutionPersistence::class.java) { flushPersistence() }
            .build()
    }

    private fun handleStart(command: StartScriptExecution) {
        val targets = command.targets.associateWith {
            MutableTarget(it, ScriptExecutionTargetStatus.Running, startedAt = Instant.now())
        }.toMutableMap()
        val execution = MutableExecution(
            command.id,
            command.script.name,
            command.script.type.name,
            command.targetType,
            targets,
            Instant.now(),
            command.targetType in setOf(ScriptExecutionTargetType.Node, ScriptExecutionTargetType.NodeRole) &&
                command.targets.isEmpty(),
            targets.size,
            targets.size,
        )
        executions.put(command.id, execution)
        evictCompletedExecutions()
        persist(execution)
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
        if (execution.finishedAt != null) {
            return
        }
        val target = result.target ?: result.actorPath ?: result.nodeAddress ?: sender.path().toString()
        val targetRecord = getOrPutTarget(execution, target)
        val nextStatus = if (result.success) {
            ScriptExecutionTargetStatus.Success
        } else {
            ScriptExecutionTargetStatus.Failed
        }
        updateTargetStatus(execution, targetRecord, nextStatus)
        targetRecord.success = result.success
        targetRecord.error = result.error
        targetRecord.nodeAddress = result.nodeAddress
        targetRecord.actorPath = result.actorPath
        targetRecord.finishedAt = Instant.now()
        refreshExecutionStatus(execution)
        markExecutionDirty(execution)
        markTargetDirty(execution.id, targetRecord)
    }

    private fun handleGet(query: GetScriptExecution) {
        val cachedExecution = executions[query.id]
        val execution = cachedExecution
            ?.takeIf { it.canServeDetailView() }
            ?.toView()
            ?: runCatching {
                repository.findById(query.id)?.toView(
                    repository.findTargets(query.id).map { it.toView() },
                )
            }
            .onFailure { logger.error(it, "load script execution:{} failed", query.id) }
            .getOrNull()
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
                updateTargetStatus(execution, it, ScriptExecutionTargetStatus.Timeout)
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
        persist(execution)
        logger.warning("script execution:{} timed out", timeout.id)
    }

    private fun markFailed(id: String, target: String, error: String) {
        val execution = executions[id] ?: return
        val targetRecord = getOrPutTarget(execution, target)
        updateTargetStatus(execution, targetRecord, ScriptExecutionTargetStatus.Failed)
        targetRecord.success = false
        targetRecord.error = error
        targetRecord.finishedAt = Instant.now()
        refreshExecutionStatus(execution)
        markExecutionDirty(execution, flushImmediately = true)
        markTargetDirty(execution.id, targetRecord, flushImmediately = true)
    }

    private fun refreshExecutionStatus(execution: MutableExecution, forceFinish: Boolean = false) {
        if (execution.dynamicTargets && !forceFinish) {
            return
        }
        if (execution.totalTargets == 0) {
            return
        }
        if (execution.runningCount > 0 && !forceFinish) {
            return
        }
        execution.status = when {
            execution.timeoutCount == execution.totalTargets -> ScriptExecutionStatus.Timeout
            execution.successCount == execution.totalTargets -> ScriptExecutionStatus.Completed
            execution.failureCount == execution.totalTargets -> ScriptExecutionStatus.Failed
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
                    evictCompletedExecutions()
                }
        }.onFailure {
            logger.error(it, "load script executions from db failed")
        }
    }

    private fun evictCompletedExecutions() {
        val iterator = executions.iterator()
        while (executions.size > MAX_EXECUTION_RECORDS && iterator.hasNext()) {
            val execution = iterator.next().value
            if (execution.status != ScriptExecutionStatus.Running) {
                iterator.remove()
            }
        }
    }

    private fun markRestoredRunningExecutionTimeout(execution: MutableExecution) {
        val now = Instant.now()
        execution.targets.values
            .filter { it.status == ScriptExecutionTargetStatus.Running }
            .forEach {
                updateTargetStatus(execution, it, ScriptExecutionTargetStatus.Timeout)
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
        persist(execution)
    }

    private fun MutableExecution.canServeDetailView(): Boolean {
        return status == ScriptExecutionStatus.Running ||
                targets.isNotEmpty() ||
                totalTargets == 0 ||
                id in dirtyExecutions
    }

    private fun getOrPutTarget(execution: MutableExecution, target: String): MutableTarget {
        return execution.targets.getOrPut(target) {
            execution.totalTargets++
            execution.runningCount++
            MutableTarget(target, ScriptExecutionTargetStatus.Running, startedAt = Instant.now())
        }
    }

    private fun updateTargetStatus(
        execution: MutableExecution,
        target: MutableTarget,
        status: ScriptExecutionTargetStatus,
    ) {
        if (target.status == status) {
            return
        }
        decrementStatusCount(execution, target.status)
        target.status = status
        incrementStatusCount(execution, status)
    }

    private fun decrementStatusCount(execution: MutableExecution, status: ScriptExecutionTargetStatus) {
        when (status) {
            ScriptExecutionTargetStatus.Running -> execution.runningCount--
            ScriptExecutionTargetStatus.Success -> execution.successCount--
            ScriptExecutionTargetStatus.Failed -> execution.failureCount--
            ScriptExecutionTargetStatus.Timeout -> execution.timeoutCount--
        }
    }

    private fun incrementStatusCount(execution: MutableExecution, status: ScriptExecutionTargetStatus) {
        when (status) {
            ScriptExecutionTargetStatus.Running -> execution.runningCount++
            ScriptExecutionTargetStatus.Success -> execution.successCount++
            ScriptExecutionTargetStatus.Failed -> execution.failureCount++
            ScriptExecutionTargetStatus.Timeout -> execution.timeoutCount++
        }
    }

    private fun markExecutionDirty(execution: MutableExecution, flushImmediately: Boolean = false) {
        dirtyExecutions.add(execution.id)
        requestPersistenceFlush(flushImmediately || execution.finishedAt != null)
    }

    private fun markTargetDirty(
        executionId: String,
        target: MutableTarget,
        flushImmediately: Boolean = false,
    ) {
        dirtyTargets["$executionId:${target.target}"] = DirtyTarget(executionId, target.toView())
        requestPersistenceFlush(flushImmediately || dirtyTargets.size >= MAX_DIRTY_TARGETS)
    }

    private fun requestPersistenceFlush(immediately: Boolean) {
        if (immediately) {
            self.tell(FlushScriptExecutionPersistence, self)
            return
        }
        if (persistenceFlushScheduled) {
            return
        }
        persistenceFlushScheduled = true
        context.system.scheduler().scheduleOnce(
            PERSISTENCE_FLUSH_INTERVAL.toJavaDuration(),
            self,
            FlushScriptExecutionPersistence,
            context.dispatcher,
            self,
        )
    }

    private fun flushPersistence() {
        persistenceFlushScheduled = false
        if (dirtyExecutions.isEmpty() && dirtyTargets.isEmpty()) {
            return
        }
        val executionDocuments = dirtyExecutions
            .mapNotNull { executions[it] }
            .map { it.toDocument() }
        val targetDocuments = dirtyTargets.values
            .map { it.target.toDocument(it.executionId) }
        runCatching {
            repository.saveExecutions(executionDocuments)
            repository.saveTargetDocuments(targetDocuments)
            dirtyExecutions.clear()
            dirtyTargets.clear()
        }.onFailure {
            logger.error(it, "flush script execution persistence failed")
            requestPersistenceFlush(immediately = false)
        }
    }

    private fun persist(execution: MutableExecution) {
        runCatching {
            repository.saveExecution(execution.toDocument())
            repository.saveTargets(execution.id, execution.targets.values.map { it.toView() })
            dirtyExecutions.remove(execution.id)
            dirtyTargets.keys.removeIf { it.startsWith("${execution.id}:") }
        }.onFailure {
            logger.error(it, "save script execution:{} failed", execution.id)
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
            totalTargets,
            successCount,
            failureCount,
            timeoutCount,
            createdAt,
            finishedAt,
            targetViews,
        )
    }

    private fun MutableExecution.toDocument(): ScriptExecutionDocument {
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

    private fun ScriptExecutionDocument.toMutableExecution(
        targetDocuments: List<ScriptExecutionTargetDocument>,
    ): MutableExecution {
        val targets = targetDocuments.associate { it.target to it.toMutableTarget() }.toMutableMap()
        val targetCount = targets.size
        return MutableExecution(
            id,
            scriptName,
            scriptType,
            targetType,
            targets,
            createdAt,
            dynamicTargets,
            totalTargets.takeIf { it > 0 } ?: targetCount,
            targets.values.count { it.status == ScriptExecutionTargetStatus.Running },
            successCount,
            failureCount,
            timeoutCount,
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
