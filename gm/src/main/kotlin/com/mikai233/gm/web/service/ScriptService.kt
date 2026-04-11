package com.mikai233.gm.web.service

import com.esotericsoftware.kryo.io.Output
import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.config.patchByVersion
import com.mikai233.common.core.Role
import com.mikai233.common.core.Singleton
import com.mikai233.common.extension.Json
import com.mikai233.common.extension.ask
import com.mikai233.common.extension.tell
import com.mikai233.common.message.ExecuteActorScript
import com.mikai233.common.message.ExecuteNodeRoleScript
import com.mikai233.common.message.ExecuteNodeScript
import com.mikai233.common.message.ExecuteScriptResult
import com.mikai233.common.script.Script
import com.mikai233.common.script.ScriptType
import com.mikai233.common.serde.KryoPool
import com.mikai233.gm.GmNode
import com.mikai233.gm.web.dto.ScriptExecutionResponse
import com.mikai233.gm.web.support.ValidateException
import com.mikai233.gm.web.support.ensure
import com.mikai233.gm.web.support.notNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.curator.x.async.api.CreateOption
import org.apache.pekko.actor.Address
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPOutputStream
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@AllOpen
@Service
class ScriptService(
    private val node: GmNode,
    private val scriptKryo: KryoPool,
) {
    fun executePlayerActorScript(
        scriptFile: MultipartFile,
        extraFile: MultipartFile?,
        playerIds: String,
    ): List<ScriptExecutionResponse> = runBlocking {
        val script = toScript(scriptFile, extraFile)
        val players = parseLongIds(playerIds, "player_id")
        ensure(players.isNotEmpty()) { "No player_id found" }
        val requestId = uuid()
        players.map { playerId ->
            async {
                node.playerSharding
                    .ask<ExecuteScriptResult>(ExecuteActorScript(playerId, requestId, script))
                    .toResponse()
            }
        }.awaitAll()
    }

    fun executeWorldActorScript(
        scriptFile: MultipartFile,
        extraFile: MultipartFile?,
        worldIds: String,
    ): List<ScriptExecutionResponse> = runBlocking {
        val script = toScript(scriptFile, extraFile)
        val worlds = parseLongIds(worldIds, "world_id")
        ensure(worlds.isNotEmpty()) { "No world_id found" }
        val missingWorlds = worlds.filter { it !in node.gameWorldMeta.worlds }
        ensure(missingWorlds.isEmpty()) {
            "worlds: ${missingWorlds.joinToString(", ")} not exists"
        }
        val requestId = uuid()
        worlds.map { worldId ->
            async {
                node.worldSharding.ask<ExecuteScriptResult>(ExecuteActorScript(worldId, requestId, script)).toResponse()
            }
        }.awaitAll()
    }

    fun executeGlobalActorScript(
        scriptFile: MultipartFile,
        extraFile: MultipartFile?,
        actorName: String,
    ): ScriptExecutionResponse = runBlocking {
        val script = toScript(scriptFile, extraFile)
        val singleton = notNull(Singleton.fromActorName(actorName)) { "No singleton actor found" }
        val requestId = uuid()
        val executeActorScript = ExecuteActorScript(0, requestId, script)
        when (singleton) {
            Singleton.Worker -> node.workerSingletonProxy.ask<ExecuteScriptResult>(executeActorScript).toResponse()
            Singleton.Monitor -> throw ValidateException("Singleton actor not supported: ${singleton.actorName}")
        }
    }

    fun executeActorScriptByPath(
        scriptFile: MultipartFile,
        extraFile: MultipartFile?,
        actorPath: String,
    ): ScriptExecutionResponse = runBlocking {
        val script = toScript(scriptFile, extraFile)
        val actorSelection = node.system.actorSelection(actorPath)
        val actorRef = runCatching {
            actorSelection.resolveOne(3.seconds.toJavaDuration()).await()
        }.getOrElse {
            throw ValidateException("Channel actor not found")
        }
        val requestId = uuid()
        actorRef.ask<ExecuteScriptResult>(ExecuteActorScript(0, requestId, script)).toResponse()
    }

    fun executeNodeRoleScript(
        scriptFile: MultipartFile,
        extraFile: MultipartFile?,
        roleName: String,
        addresses: List<String>,
        patch: Boolean,
    ) = runBlocking {
        val script = toScript(scriptFile, extraFile)
        val role = runCatching { Role.valueOf(roleName) }
            .getOrElse { throw ValidateException("No script role found") }
        val nodeRoleScript = ExecuteNodeRoleScript(uuid(), script, role, parseAddresses(addresses))
        if (patch) {
            uploadPatch(nodeRoleScript)
        }
        node.scriptRouter.tell(nodeRoleScript)
    }

    fun executeNodeScript(
        scriptFile: MultipartFile,
        extraFile: MultipartFile?,
        addresses: List<String>,
    ) = runBlocking {
        val script = toScript(scriptFile, extraFile)
        node.scriptRouter.tell(ExecuteNodeScript(uuid(), script, parseAddresses(addresses)))
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

    private fun parseLongIds(rawIds: String, fieldName: String): Set<Long> {
        return rawIds.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapTo(linkedSetOf()) { rawId ->
                notNull(rawId.toLongOrNull()) { "$fieldName:$rawId must be a number" }
            }
    }

    private fun parseAddresses(addresses: List<String>): Set<Address> {
        return addresses.filter { it.isNotBlank() }
            .mapTo(linkedSetOf()) { Json.fromStr<Address>(it) }
    }

    private fun Result<ExecuteScriptResult>.toResponse(): ScriptExecutionResponse {
        return ScriptExecutionResponse.from(this)
    }

    private fun uuid(): String = UUID.randomUUID().toString()
}
