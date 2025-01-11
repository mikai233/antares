package com.mikai233.gm.web.route

import com.mikai233.common.core.Singleton
import com.mikai233.common.extension.ask
import com.mikai233.common.message.ExecuteActorScript
import com.mikai233.common.message.ExecuteScriptResult
import com.mikai233.common.script.Script
import com.mikai233.common.script.ScriptType
import com.mikai233.gm.web.models.ScriptPart
import com.mikai233.gm.web.node
import com.mikai233.gm.web.plugins.ValidateException
import com.mikai233.gm.web.plugins.ensure
import com.mikai233.gm.web.plugins.notNull
import com.mikai233.gm.web.uuid
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.await
import kotlinx.io.readByteArray
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

fun Application.scriptRoutes() {
    suspend fun RoutingContext.executeWorldActorScript() {
        val multipart = call.receiveMultipart()
        val allWorlds = linkedSetOf<Long>()
        val script = receiveFormAndScript(multipart, "world_id") { worldIdStr ->
            val worlds = worldIdStr.split(",").map { notNull(it.toLongOrNull()) { "world id:${it} must be a number" } }
            allWorlds.addAll(worlds)
        }
        ensure(allWorlds.isNotEmpty()) { "No world_id found" }
        val node = node()
        val noneExistsWorlds = allWorlds.filter { it !in node.gameWorldMeta.worlds }
        ensure(noneExistsWorlds.isEmpty()) {
            "worlds: ${noneExistsWorlds.joinToString(", ")} not exists"
        }
        val uuid = uuid()
        val results = allWorlds.map { worldId ->
            val executeActorScript = ExecuteActorScript(worldId, uuid, script)
            async { node.worldSharding.ask<ExecuteScriptResult>(executeActorScript) }
        }.awaitAll()
        call.respond(results)
    }

    suspend fun RoutingContext.executePlayerActorScript() {
        val multipart = call.receiveMultipart()
        val allPlayers = linkedSetOf<Long>()
        val script = receiveFormAndScript(multipart, "player_id") { worldIdStr ->
            val worlds = worldIdStr.split(",").map { notNull(it.toLongOrNull()) { "player id:${it} must be a number" } }
            allPlayers.addAll(worlds)
        }
        ensure(allPlayers.isNotEmpty()) { "No player_id found" }
        val node = node()
        val uuid = uuid()
        //TODO 对于在线的Actor直接发送，不在线的Actor使用退避算法间隔发送，防止短时间拉起大量Actor造成OOM
        val results = allPlayers.map { playerId ->
            val executeActorScript = ExecuteActorScript(playerId, uuid, script)
            async { node.playerSharding.ask<ExecuteScriptResult>(executeActorScript) }
        }.awaitAll()
        call.respond(results)
    }

    suspend fun RoutingContext.executeGlobalActorScript() {
        val multipart = call.receiveMultipart()
        var nullableActorName: String? = null
        val script = receiveFormAndScript(multipart, "actor_name") { nullableActorName = it }
        val actorName = notNull(nullableActorName) { "No actor_name found" }
        val singleton = notNull(Singleton.fromActorName(actorName)) { "No singleton actor found" }
        val node = node()
        val uuid = uuid()
        val executeActorScript = ExecuteActorScript(0, uuid, script)
        when (singleton) {
            Singleton.Worker -> {
                val result = node.workerSingletonProxy.ask<ExecuteScriptResult>(executeActorScript)
                call.respond(result)
            }

            Singleton.Monitor -> TODO()
        }
    }

    suspend fun RoutingContext.executeChannelActorScript() {
        val multipart = call.receiveMultipart()
        var nullablePath: String? = null
        val script = receiveFormAndScript(multipart, "path") { nullablePath = it }
        val path = notNull(nullablePath) { "No path found" }
        val node = node()
        val channelActorSelection = node.system.actorSelection(path)
        runCatching {
            channelActorSelection.resolveOne(3.seconds.toJavaDuration()).await()
        }.onSuccess { channelActor ->
            val uuid = uuid()
            val executeActorScript = ExecuteActorScript(0, uuid, script)
            val result = channelActor.ask<ExecuteScriptResult>(executeActorScript)
            call.respond(result)
        }.onFailure {
            call.respond(HttpStatusCode.BadGateway, "Channel actor not found")
        }
    }

    routing {
        route("/script") {
            post("player_actor_script") { executePlayerActorScript() }
            post("world_actor_script") { executeWorldActorScript() }
            post("global_actor_script") { executeGlobalActorScript() }
            post("channel_actor_script") { executeChannelActorScript() }
            post("node_script") { }
            post("node_role_script") { }
        }
    }
}

suspend fun receiveFormAndScript(
    multipart: MultiPartData,
    formName: String,
    transform: (String) -> Unit
): Script {
    var nullableScriptPart: ScriptPart? = null
    var extra: ByteArray? = null
    multipart.forEachPart { part ->
        when (part) {
            is PartData.FileItem -> {
                if (part.name == "script") {
                    ensure(nullableScriptPart == null) { "Only one script file is allowed" }
                    nullableScriptPart = receiveScriptPart(part)
                } else if (part.name == "extra") {
                    extra = part.provider().readRemaining().readByteArray()
                }
            }

            is PartData.FormItem -> {
                if (part.name == formName) {
                    transform(part.value)
                }
            }

            else -> throw ValidateException("Unsupported part type:${part::class.simpleName}")
        }
    }
    val scriptPart = notNull(nullableScriptPart) { "No script file found" }
    return Script(scriptPart.name, scriptPart.type, scriptPart.body, extra)
}

suspend fun receiveScriptPart(part: PartData.FileItem): ScriptPart {
    val originalFileName = notNull(part.originalFileName) { "No file name" }
    val path = Path(originalFileName)
    val extension = path.extension
    ensure(extension == "jar" || extension == "groovy") { "File extension must be jar or groovy" }
    val scriptType = when (extension) {
        "jar" -> ScriptType.KotlinScript
        "groovy" -> ScriptType.GroovyScript
        else -> throw IllegalArgumentException("Unsupported script type")
    }
    val fileBytes = part.provider().readRemaining().readByteArray()
    return ScriptPart(path.nameWithoutExtension, scriptType, fileBytes)
}