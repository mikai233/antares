package com.mikai233.gm.web.route

import com.mikai233.common.core.Singleton
import com.mikai233.common.extension.ask
import com.mikai233.common.message.ExecuteActorScript
import com.mikai233.common.message.ExecuteScriptResult
import com.mikai233.common.script.Script
import com.mikai233.common.script.ScriptType
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
        val (worlds, script) = receiveIdAndScript(multipart)
        ensure(worlds.isNotEmpty()) { "No id found" }
        //TODO: check worldId exists
        val node = node()
        val uuid = uuid()
        val results = worlds.map { worldId ->
            val executeActorScript = ExecuteActorScript(worldId, uuid, script)
            async { node.worldSharding.ask<ExecuteScriptResult>(executeActorScript) }
        }.awaitAll()
        call.respond(results)
    }

    suspend fun RoutingContext.executePlayerActorScript() {
        val multipart = call.receiveMultipart()
        val (players, script) = receiveIdAndScript(multipart)
        ensure(players.isNotEmpty()) { "No id found" }
        val node = node()
        val uuid = uuid()
        val results = players.map { playerId ->
            val executeActorScript = ExecuteActorScript(playerId, uuid, script)
            async { node.playerSharding.ask<ExecuteScriptResult>(executeActorScript) }
        }.awaitAll()
        call.respond(results)
    }

    suspend fun RoutingContext.executeGlobalActorScript() {
        val multipart = call.receiveMultipart()
        val (actorName, script) = receiveNameAndScript(multipart)
        val singleton = notNull(Singleton.fromActorName(actorName)) { "No actor found" }
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
        val (path, script) = receivePathAndScript(multipart)
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

suspend fun receivePathAndScript(multipart: MultiPartData): Pair<String, Script> {
    var path: String? = null
    var script: Script? = null
    multipart.forEachPart { part ->
        when (part) {
            is PartData.FileItem -> {
                ensure(script == null) { "Only one script file is allowed" }
                script = receiveScript(part)
            }

            is PartData.FormItem -> {
                if (part.name == "path") {
                    ensure(path == null) { "Only one path is allowed" }
                    path = part.value
                }
            }

            else -> throw ValidateException("Unsupported part type:${part::class.simpleName}")
        }
    }
    return Pair(notNull(path) { "No path found" }, notNull(script) { "No script file found" })
}

suspend fun receiveNameAndScript(multipart: MultiPartData): Pair<String, Script> {
    var name: String? = null
    var script: Script? = null
    multipart.forEachPart { part ->
        when (part) {
            is PartData.FileItem -> {
                ensure(script == null) { "Only one script file is allowed" }
                script = receiveScript(part)
            }

            is PartData.FormItem -> {
                if (part.name == "name") {
                    ensure(name == null) { "Only one name is allowed" }
                    name = part.value
                }
            }

            else -> throw ValidateException("Unsupported part type:${part::class.simpleName}")
        }
    }
    return Pair(notNull(name) { "No name found" }, notNull(script) { "No script file found" })
}

suspend fun receiveIdAndScript(multipart: MultiPartData): Pair<LinkedHashSet<Long>, Script> {
    val players: LinkedHashSet<Long> = linkedSetOf()
    var script: Script? = null
    multipart.forEachPart { part ->
        when (part) {
            is PartData.FileItem -> {
                ensure(script == null) { "Only one script file is allowed" }
                script = receiveScript(part)
            }

            is PartData.FormItem -> {
                if (part.name == "id") {
                    part.value.split(",").map {
                        notNull(it.toLongOrNull()) { "id:${it} must be a number" }
                    }.let {
                        players.addAll(it)
                    }
                }
            }

            else -> throw ValidateException("Unsupported part type:${part::class.simpleName}")
        }
    }
    return Pair(players, notNull(script) { "No script file found" })
}

suspend fun receiveScript(part: PartData.FileItem): Script {
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
    return Script(path.nameWithoutExtension, scriptType, fileBytes)
}