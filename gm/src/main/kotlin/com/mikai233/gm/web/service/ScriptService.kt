package com.mikai233.gm.web.service

import com.esotericsoftware.kryo.io.Output
import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.config.patchByVersion
import com.mikai233.common.core.Role
import com.mikai233.common.core.Singleton
import com.mikai233.common.extension.Json
import com.mikai233.common.extension.ask
import com.mikai233.common.message.ExecuteNodeRoleScript
import com.mikai233.common.message.Message
import com.mikai233.common.script.Script
import com.mikai233.common.script.ScriptType
import com.mikai233.common.serde.KryoPool
import com.mikai233.gm.GmNode
import com.mikai233.gm.script.*
import com.mikai233.gm.web.dto.CreateScriptExecutionRequest
import com.mikai233.gm.web.support.ValidateException
import com.mikai233.gm.web.support.ensure
import com.mikai233.gm.web.support.notNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.apache.curator.x.async.api.CreateOption
import org.apache.pekko.actor.Address
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.zip.GZIPOutputStream
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

@AllOpen
@Service
class ScriptService(
    private val node: GmNode,
    private val scriptKryo: KryoPool,
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

    private suspend fun executePlayerActorScript(script: Script, targets: List<String>): ScriptExecutionView {
        val players = parseLongTargets(targets, "player_id")
        ensure(players.isNotEmpty()) { "No player_id found" }
        return startExecution(
            script,
            ScriptExecutionTargetType.PlayerActor,
            players.mapTo(linkedSetOf()) { it.toString() },
        )
    }

    private suspend fun executeWorldActorScript(script: Script, targets: List<String>): ScriptExecutionView {
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

    private suspend fun executeGlobalActorScript(script: Script, targets: List<String>): ScriptExecutionView {
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

    private suspend fun executeActorScriptByPath(script: Script, targets: List<String>): ScriptExecutionView {
        val actorPath = singleTarget(targets, "actor_path")
        ensure(actorPath.isNotBlank()) { "No actor_path found" }
        return startExecution(script, ScriptExecutionTargetType.ActorPath, setOf(actorPath))
    }

    private suspend fun executeNodeRoleScript(
        script: Script,
        request: CreateScriptExecutionRequest,
    ): ScriptExecutionView {
        val roleName = notNull(request.role?.takeIf { it.isNotBlank() }) { "No script role found" }
        val role = runCatching { Role.valueOf(roleName) }
            .getOrElse { throw ValidateException("No script role found") }
        val parsedAddresses = parseAddresses(request.addresses)
        val id = uuid()
        val nodeRoleScript = ExecuteNodeRoleScript(id, script, role, parsedAddresses)
        if (request.patch) {
            uploadPatch(nodeRoleScript)
        }
        return startExecutionAsync(
            id,
            script,
            ScriptExecutionTargetType.NodeRole,
            parsedAddresses.mapTo(linkedSetOf()) { it.toString() },
            parsedAddresses,
            role,
        )
    }

    private suspend fun executeNodeScript(script: Script, addresses: List<String>): ScriptExecutionView {
        val parsedAddresses = parseAddresses(addresses)
        return startExecution(
            script,
            ScriptExecutionTargetType.Node,
            parsedAddresses.mapTo(linkedSetOf()) { it.toString() },
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

    private suspend fun uploadPatch(nodeRoleScript: ExecuteNodeRoleScript) {
        val bytes = withContext(Dispatchers.IO) {
            val bos = ByteArrayOutputStream()
            Output(GZIPOutputStream(bos)).use {
                scriptKryo.use { writeClassAndObject(it, nodeRoleScript) }
            }
            bos.toByteArray()
        }
        val patchByVersion = patchByVersion(node.version())
        val scriptPath = "${patchByVersion}/${nodeRoleScript.script.name}"
        node.zookeeper.create()
            .withOptions(setOf(CreateOption.createParentsIfNeeded, CreateOption.setDataIfExists))
            .forPath(scriptPath, bytes)
            .await()
    }

    private fun toScript(scriptFile: MultipartFile, extraFile: MultipartFile?): Script {
        val originalFileName = notNull(scriptFile.originalFilename) { "No file name" }
        val path = Path(originalFileName)
        val extension = path.extension.lowercase()
        ensure(extension == "jar" || extension == "groovy") { "File extension must be jar or groovy" }
        val scriptType = when (extension) {
            "jar" -> ScriptType.JarScript
            "groovy" -> ScriptType.GroovyScript
            else -> error("Unsupported script type: $extension")
        }
        return Script(
            path.nameWithoutExtension,
            scriptType,
            scriptFile.bytes,
            extraFile?.bytes,
        )
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

    private fun parseAddresses(addresses: List<String>): Set<Address> {
        return addresses.filter { it.isNotBlank() }
            .mapTo(linkedSetOf()) { Json.fromStr<Address>(it) }
    }

    private suspend fun startExecution(
        script: Script,
        targetType: ScriptExecutionTargetType,
        targets: Set<String>,
        addressFilter: Set<Address> = emptySet(),
        role: Role? = null,
    ): ScriptExecutionView {
        return startExecution(uuid(), script, targetType, targets, addressFilter, role)
    }

    private suspend fun startExecution(
        id: String,
        script: Script,
        targetType: ScriptExecutionTargetType,
        targets: Set<String>,
        addressFilter: Set<Address> = emptySet(),
        role: Role? = null,
    ): ScriptExecutionView = startExecutionAsync(id, script, targetType, targets, addressFilter, role)

    private suspend fun startExecutionAsync(
        id: String,
        script: Script,
        targetType: ScriptExecutionTargetType,
        targets: Set<String>,
        addressFilter: Set<Address> = emptySet(),
        role: Role? = null,
    ): ScriptExecutionView {
        return node.scriptExecutionManager.ask<ScriptExecutionView>(
            StartScriptExecution(id, script, targetType, targets, addressFilter, role),
        ).getOrThrow()
    }

    private fun uuid(): String = UUID.randomUUID().toString()
}
