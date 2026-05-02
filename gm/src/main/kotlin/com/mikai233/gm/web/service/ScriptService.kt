package com.mikai233.gm.web.service

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.core.Role
import com.mikai233.common.core.Singleton
import com.mikai233.common.extension.ask
import com.mikai233.common.message.Message
import com.mikai233.gm.GmNode
import com.mikai233.gm.script.*
import com.mikai233.gm.web.dto.CreateScriptExecutionRequest
import com.mikai233.gm.web.support.ValidateException
import com.mikai233.gm.web.support.ensure
import com.mikai233.gm.web.support.notNull
import io.github.mikai233.asteria.script.ScriptArtifact
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

@AllOpen
@Service
class ScriptService(
    private val node: GmNode,
) {
    suspend fun createExecution(
        scriptFile: MultipartFile,
        extraFile: MultipartFile?,
        request: CreateScriptExecutionRequest,
    ): ScriptExecutionView {
        val script = toScript(scriptFile, extraFile)
        return when (request.targetType) {
            ScriptExecutionTargetType.PlayerActor -> executePlayerActorScript(script, request.targets)
            ScriptExecutionTargetType.WorldActor -> executeWorldActorScript(script, request.targets)
            ScriptExecutionTargetType.GlobalActor -> executeGlobalActorScript(script, request.targets)
            ScriptExecutionTargetType.ActorPath -> executeActorScriptByPath(script, request.targets)
            ScriptExecutionTargetType.Node -> executeNodeScript(script, request.addresses)
            ScriptExecutionTargetType.NodeRole -> executeNodeRoleScript(script, request)
        }
    }

    private suspend fun executePlayerActorScript(script: ScriptArtifact, targets: List<String>): ScriptExecutionView {
        val players = parseLongTargets(targets, "player_id")
        ensure(players.isNotEmpty()) { "No player_id found" }
        return startExecution(
            script,
            ScriptExecutionTargetType.PlayerActor,
            players.mapTo(linkedSetOf()) { it.toString() },
        )
    }

    private suspend fun executeWorldActorScript(script: ScriptArtifact, targets: List<String>): ScriptExecutionView {
        val worlds = parseLongTargets(targets, "world_id")
        ensure(worlds.isNotEmpty()) { "No world_id found" }
        val missingWorlds = worlds.filter { it !in node.gameWorldMeta.worlds }
        ensure(missingWorlds.isEmpty()) {
            "worlds: ${missingWorlds.joinToString(", ")} not exists"
        }
        return startExecution(
            script,
            ScriptExecutionTargetType.WorldActor,
            worlds.mapTo(linkedSetOf()) { it.toString() },
        )
    }

    private suspend fun executeGlobalActorScript(script: ScriptArtifact, targets: List<String>): ScriptExecutionView {
        val actorName = singleTarget(targets, "actor_name")
        when (val singleton = notNull(Singleton.fromActorName(actorName)) { "No singleton actor found" }) {
            Singleton.Worker -> return startExecution(
                script,
                ScriptExecutionTargetType.GlobalActor,
                setOf(actorName),
            )

            Singleton.Monitor -> throw ValidateException("Singleton actor not supported: ${singleton.actorName}")
        }
    }

    private suspend fun executeActorScriptByPath(script: ScriptArtifact, targets: List<String>): ScriptExecutionView {
        val actorPath = singleTarget(targets, "actor_path")
        ensure(actorPath.isNotBlank()) { "No actor_path found" }
        return startExecution(script, ScriptExecutionTargetType.ActorPath, setOf(actorPath))
    }

    private suspend fun executeNodeRoleScript(
        script: ScriptArtifact,
        request: CreateScriptExecutionRequest,
    ): ScriptExecutionView {
        ensure(!request.patch) {
            "Script patch upload is not supported after migrating to Asteria ScriptRuntime"
        }
        val roleName = notNull(request.role?.takeIf { it.isNotBlank() }) { "No script role found" }
        val role = runCatching { Role.valueOf(roleName) }
            .getOrElse { throw ValidateException("No script role found") }
        val parsedAddresses = parseAddresses(request.addresses)
        ensure(parsedAddresses.isEmpty()) {
            "Asteria ScriptTarget.Role does not support role + address filtering yet"
        }
        val id = uuid()
        return startExecutionAsync(
            id,
            script,
            ScriptExecutionTargetType.NodeRole,
            emptySet(),
            emptySet(),
            role,
        )
    }

    private suspend fun executeNodeScript(script: ScriptArtifact, addresses: List<String>): ScriptExecutionView {
        val parsedAddresses = parseAddresses(addresses)
        return startExecution(
            script,
            ScriptExecutionTargetType.Node,
            parsedAddresses,
            parsedAddresses,
        )
    }

    suspend fun listExecutions(): List<ScriptExecutionView> {
        return node.scriptExecutionManager.ask<ScriptExecutionListView>(ListScriptExecutions)
            .getOrThrow()
            .executions
    }

    suspend fun getExecution(id: String): ScriptExecutionView {
        return when (val response = node.scriptExecutionManager.ask<Message>(GetScriptExecution(id)).getOrThrow()) {
            is ScriptExecutionView -> response
            is ScriptExecutionNotFound -> throw ValidateException("Script execution not found: ${response.id}")
            else -> error("Unsupported script execution response: ${response::class.qualifiedName}")
        }
    }

    private fun toScript(scriptFile: MultipartFile, extraFile: MultipartFile?): ScriptArtifact {
        val originalFileName = notNull(scriptFile.originalFilename) { "No file name" }
        val path = Path(originalFileName)
        val extension = path.extension.lowercase()
        ensure(extension == "jar" || extension == "groovy") { "File extension must be jar or groovy" }
        val engine = when (extension) {
            "jar" -> "jar"
            "groovy" -> "groovy"
            else -> error("Unsupported script type: $extension")
        }
        return ScriptArtifact(path.nameWithoutExtension, engine, scriptFile.bytes, extraFile?.bytes)
    }

    private fun parseLongTargets(rawTargets: List<String>, fieldName: String): Set<Long> {
        return normalizeTargets(rawTargets)
            .mapTo(linkedSetOf()) { rawId ->
                notNull(rawId.toLongOrNull()) { "$fieldName:$rawId must be a number" }
            }
    }

    private fun singleTarget(rawTargets: List<String>, fieldName: String): String {
        val targets = normalizeTargets(rawTargets)
        ensure(targets.size == 1) { "Exactly one $fieldName is required" }
        return targets.single()
    }

    private fun normalizeTargets(rawTargets: List<String>): List<String> {
        return rawTargets.asSequence()
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    private fun parseAddresses(addresses: List<String>): Set<String> {
        return addresses.filter { it.isNotBlank() }
            .mapTo(linkedSetOf()) { it.trim() }
    }

    private suspend fun startExecution(
        script: ScriptArtifact,
        targetType: ScriptExecutionTargetType,
        targets: Set<String>,
        addressFilter: Set<String> = emptySet(),
        role: Role? = null,
    ): ScriptExecutionView {
        return startExecution(uuid(), script, targetType, targets, addressFilter, role)
    }

    private suspend fun startExecution(
        id: String,
        script: ScriptArtifact,
        targetType: ScriptExecutionTargetType,
        targets: Set<String>,
        addressFilter: Set<String> = emptySet(),
        role: Role? = null,
    ): ScriptExecutionView = startExecutionAsync(id, script, targetType, targets, addressFilter, role)

    private suspend fun startExecutionAsync(
        id: String,
        script: ScriptArtifact,
        targetType: ScriptExecutionTargetType,
        targets: Set<String>,
        addressFilter: Set<String> = emptySet(),
        role: Role? = null,
    ): ScriptExecutionView {
        return node.scriptExecutionManager.ask<ScriptExecutionView>(
            StartScriptExecution(id, script, targetType, targets, addressFilter, role),
        ).getOrThrow()
    }

    private fun uuid(): String = UUID.randomUUID().toString()
}
