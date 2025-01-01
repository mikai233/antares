package com.mikai233.gm.web.route

import com.mikai233.common.extension.ask
import com.mikai233.common.message.ExecuteActorScript
import com.mikai233.common.message.ExecuteScriptResult
import com.mikai233.common.script.Script
import com.mikai233.common.script.ScriptType
import com.mikai233.gm.web.models.ChannelActorScript
import com.mikai233.gm.web.models.GlobalActorScript
import com.mikai233.gm.web.models.WorldActorScript
import com.mikai233.gm.web.node
import com.mikai233.gm.web.plugins.ValidateException
import com.mikai233.gm.web.plugins.ensure
import com.mikai233.gm.web.plugins.notNull
import com.mikai233.gm.web.uuid
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.io.readByteArray
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

fun Application.scriptRoutes() {
    routing {
        route("/script") {
            post("player_actor_script") {
                val multipart = call.receiveMultipart()
                val players: LinkedHashSet<Long> = linkedSetOf()
                var script: Script? = null
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            ensure(script == null) { "Only one script file is allowed" }
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
                            script = Script(path.nameWithoutExtension, scriptType, fileBytes)
                        }

                        is PartData.FormItem -> {
                            if (part.name == "player_id") {
                                part.value.split(",").map {
                                    notNull(it.toLongOrNull()) { "player_id:${it} must be a number" }
                                }.let {
                                    players.addAll(it)
                                }
                            }
                        }

                        else -> throw ValidateException("Unsupported part type:${part::class.simpleName}")
                    }
                }
                notNull(script) { "No script file found" }
                ensure(players.isNotEmpty()) { "No player_id found" }
                val node = node()
                val uuid = uuid()
                val results = players.map { playerId ->
                    val executeActorScript = ExecuteActorScript(playerId, uuid, script)
                    async { node.playerSharding.ask<ExecuteScriptResult>(executeActorScript) }
                }.awaitAll()
                call.respond(results)
                call.respondText { "ok" }
            }
            post<WorldActorScript>("world_actor_script") { }
            post<GlobalActorScript>("global_actor_script") { }
            post<ChannelActorScript>("channel_actor_script") { }
            post("node_script") { }
            post("node_role_script") { }
        }
    }
}