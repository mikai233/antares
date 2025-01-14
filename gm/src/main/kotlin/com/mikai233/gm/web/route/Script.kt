package com.mikai233.gm.web.route

import akka.actor.Address
import com.esotericsoftware.kryo.io.Output
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
import com.mikai233.gm.web.kryo
import com.mikai233.gm.web.models.ScriptPart
import com.mikai233.gm.web.node
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import org.apache.curator.x.async.api.CreateOption
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

fun Application.scriptRoutes() {
    suspend fun RoutingContext.executePlayerActorScript() {
        val multipart = call.receiveMultipart()
        var nullableScriptPart: ScriptPart? = null
        var nullableExtraBytes: ByteArray? = null
        val players = linkedSetOf<Long>()

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    when (part.name) {
                        "script" -> {
                            nullableScriptPart = receiveScriptPart(part)
                        }

                        "extra" -> {
                            nullableExtraBytes = part.provider().readRemaining().readByteArray()
                        }

                        else -> Unit
                    }
                }

                is PartData.FormItem -> {
                    when (part.name) {
                        "player_id" -> {
                            part.value
                                .split(",")
                                .map { notNull(it.toLongOrNull()) { "player_id:${it} must be a number" } }
                                .let { players.addAll(it) }
                        }

                        else -> Unit
                    }
                }

                else -> Unit
            }
        }
        val scriptPart = notNull(nullableScriptPart) { "No script file found" }
        val script = Script(scriptPart.name, scriptPart.type, scriptPart.body, nullableExtraBytes)
        ensure(players.isNotEmpty()) { "No player_id found" }
        val node = node()
        val uuid = uuid()
        //TODO 对于在线的Actor直接发送，不在线的Actor使用退避算法间隔发送，防止短时间拉起大量Actor造成OOM
        val results = players.map { playerId ->
            val executeActorScript = ExecuteActorScript(playerId, uuid, script)
            async { node.playerSharding.ask<ExecuteScriptResult>(executeActorScript) }
        }.awaitAll()
        call.respond(results)
    }

    suspend fun RoutingContext.executeWorldActorScript() {
        val multipart = call.receiveMultipart()
        var nullableScriptPart: ScriptPart? = null
        var nullableExtraBytes: ByteArray? = null
        val worlds = linkedSetOf<Long>()

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    when (part.name) {
                        "script" -> {
                            nullableScriptPart = receiveScriptPart(part)
                        }

                        "extra" -> {
                            nullableExtraBytes = part.provider().readRemaining().readByteArray()
                        }

                        else -> Unit
                    }
                }

                is PartData.FormItem -> {
                    when (part.name) {
                        "player_id" -> {
                            part.value
                                .split(",")
                                .map { notNull(it.toLongOrNull()) { "world_id:${it} must be a number" } }
                                .let { worlds.addAll(it) }
                        }

                        else -> Unit
                    }
                }

                else -> Unit
            }
        }
        val scriptPart = notNull(nullableScriptPart) { "No script file found" }
        val script = Script(scriptPart.name, scriptPart.type, scriptPart.body, nullableExtraBytes)
        ensure(worlds.isNotEmpty()) { "No world_id found" }
        val node = node()
        val noneExistsWorlds = worlds.filter { it !in node.gameWorldMeta.worlds }
        ensure(noneExistsWorlds.isEmpty()) {
            "worlds: ${noneExistsWorlds.joinToString(", ")} not exists"
        }
        val uuid = uuid()
        val results = worlds.map { worldId ->
            val executeActorScript = ExecuteActorScript(worldId, uuid, script)
            async { node.worldSharding.ask<ExecuteScriptResult>(executeActorScript) }
        }.awaitAll()
        call.respond(results)
    }

    suspend fun RoutingContext.executeGlobalActorScript() {
        val multipart = call.receiveMultipart()
        var nullableScriptPart: ScriptPart? = null
        var nullableExtraBytes: ByteArray? = null
        var nullableActorName: String? = null

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    when (part.name) {
                        "script" -> {
                            nullableScriptPart = receiveScriptPart(part)
                        }

                        "extra" -> {
                            nullableExtraBytes = part.provider().readRemaining().readByteArray()
                        }

                        else -> Unit
                    }
                }

                is PartData.FormItem -> {
                    when (part.name) {
                        "actor_name" -> {
                            nullableActorName = part.value
                        }

                        else -> Unit
                    }
                }

                else -> Unit
            }
        }
        val scriptPart = notNull(nullableScriptPart) { "No script file found" }
        val script = Script(scriptPart.name, scriptPart.type, scriptPart.body, nullableExtraBytes)
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

    suspend fun RoutingContext.executeActorScriptByPath() {
        val multipart = call.receiveMultipart()
        var nullableScriptPart: ScriptPart? = null
        var nullableExtraBytes: ByteArray? = null
        var nullableActorPath: String? = null

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    when (part.name) {
                        "script" -> {
                            nullableScriptPart = receiveScriptPart(part)
                        }

                        "extra" -> {
                            nullableExtraBytes = part.provider().readRemaining().readByteArray()
                        }

                        else -> Unit
                    }
                }

                is PartData.FormItem -> {
                    when (part.name) {
                        "actor_path" -> {
                            nullableActorPath = part.value
                        }

                        else -> Unit
                    }
                }

                else -> Unit
            }
        }
        val scriptPart = notNull(nullableScriptPart) { "No script file found" }
        val script = Script(scriptPart.name, scriptPart.type, scriptPart.body, nullableExtraBytes)
        val actorPath = notNull(nullableActorPath) { "No actor_path found" }
        val node = node()
        val actorSelection = node.system.actorSelection(actorPath)
        runCatching {
            actorSelection.resolveOne(3.seconds.toJavaDuration()).await()
        }.onSuccess { channelActor ->
            val uuid = uuid()
            val executeActorScript = ExecuteActorScript(0, uuid, script)
            val result = channelActor.ask<ExecuteScriptResult>(executeActorScript)
            call.respond(result)
        }.onFailure {
            call.respond(HttpStatusCode.BadGateway, "Channel actor not found")
        }
    }


    suspend fun RoutingContext.executeNodeRoleScript() {
        val multipart = call.receiveMultipart()
        var nullableScriptPart: ScriptPart? = null
        var nullableExtraBytes: ByteArray? = null
        var isPatch = false
        val addresses = mutableSetOf<Address>()
        var nullableRole: Role? = null

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    when (part.name) {
                        "script" -> {
                            nullableScriptPart = receiveScriptPart(part)
                        }

                        "extra" -> {
                            nullableExtraBytes = part.provider().readRemaining().readByteArray()
                        }

                        else -> Unit
                    }
                }

                is PartData.FormItem -> {
                    when (part.name) {
                        "patch" -> {
                            isPatch = true
                        }

                        "address" -> {
                            addresses.add(Json.fromStr(part.value))
                        }

                        "role" -> {
                            nullableRole = Role.valueOf(part.value)
                        }

                        else -> Unit
                    }
                }

                else -> Unit
            }
        }
        val scriptPart = notNull(nullableScriptPart) { "No script file found" }
        val script = Script(scriptPart.name, scriptPart.type, scriptPart.body, nullableExtraBytes)
        val role = notNull(nullableRole) { "No script role found" }
        val node = node()
        val nodeRoleScript = ExecuteNodeRoleScript(uuid(), script, role, addresses)
        if (isPatch) {
            //upload script to zookeeper
            val bytes = withContext(Dispatchers.IO) {
                val bos = ByteArrayOutputStream()
                Output(GZIPOutputStream(bos)).use {
                    kryo().use { writeClassAndObject(it, nodeRoleScript) }
                }
                bos.toByteArray()
            }
            val patchByVersion = patchByVersion(node.version())
            val scriptPath = "${patchByVersion}/${script.name}"
            node.zookeeper.create()
                .withOptions(setOf(CreateOption.createParentsIfNeeded, CreateOption.setDataIfExists))
                .forPath(scriptPath, bytes)
                .await()
        }
        node.scriptRouter.tell(nodeRoleScript)
        call.respond(HttpStatusCode.OK)
    }


    suspend fun RoutingContext.executeNodeScript() {
        val multipart = call.receiveMultipart()
        var nullableScriptPart: ScriptPart? = null
        var nullableExtraBytes: ByteArray? = null
        val addresses = mutableSetOf<Address>()

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    when (part.name) {
                        "script" -> {
                            nullableScriptPart = receiveScriptPart(part)
                        }

                        "extra" -> {
                            nullableExtraBytes = part.provider().readRemaining().readByteArray()
                        }

                        else -> Unit
                    }
                }

                is PartData.FormItem -> {
                    when (part.name) {
                        "address" -> {
                            addresses.add(Json.fromStr(part.value))
                        }

                        else -> Unit
                    }
                }

                else -> Unit
            }
        }
        val scriptPart = notNull(nullableScriptPart) { "No script file found" }
        val script = Script(scriptPart.name, scriptPart.type, scriptPart.body, nullableExtraBytes)
        val node = node()
        node.scriptRouter.tell(ExecuteNodeScript(uuid(), script, addresses))
        call.respond(HttpStatusCode.OK)
    }

    routing {
        route("/script") {
            post("player_actor_script") { executePlayerActorScript() }
            post("world_actor_script") { executeWorldActorScript() }
            post("global_actor_script") { executeGlobalActorScript() }
            post("channel_actor_script") { executeActorScriptByPath() }
            post("node_script") { executeNodeScript() }
            post("node_role_script") { executeNodeRoleScript() }
        }
    }
}

private suspend fun receiveScriptPart(part: PartData.FileItem): ScriptPart {
    val originalFileName = notNull(part.originalFileName) { "No file name" }
    val path = Path(originalFileName)
    val extension = path.extension
    ensure(extension == "jar" || extension == "groovy") { "File extension must be jar or groovy" }
    val scriptType = when (extension) {
        "jar" -> ScriptType.JarScript
        "groovy" -> ScriptType.GroovyScript
        else -> throw IllegalArgumentException("Unsupported script type")
    }
    val fileBytes = part.provider().readRemaining().readByteArray()
    return ScriptPart(path.nameWithoutExtension, scriptType, fileBytes)
}