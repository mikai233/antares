package com.mikai233.common.core

import akka.actor.Address
import com.esotericsoftware.kryo.io.Input
import com.mikai233.common.config.patchByVersion
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.common.message.ExecuteActorScript
import com.mikai233.common.message.ExecuteNodeRoleScript
import com.mikai233.common.message.ExecuteNodeScript
import com.mikai233.common.script.Script
import com.mikai233.common.script.ScriptType
import com.mikai233.common.serde.DepsExtra
import com.mikai233.common.serde.KryoPool
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import java.util.zip.GZIPInputStream

class Patcher(private val node: Node) {
    companion object {
        fun scriptKryo(): KryoPool {
            val scriptDeps = arrayOf(
                ExecuteNodeRoleScript::class,
                ExecuteNodeScript::class,
                ExecuteActorScript::class,
                Script::class,
                Role::class,
                Address::class,
                ScriptType::class
            )
            return KryoPool(scriptDeps + DepsExtra)
        }
    }

    private val logger = logger()
    private val kryo = scriptKryo()

    suspend fun apply() {
        val patchByVersion = patchByVersion(node.version())
        val patches = coroutineScope {
            node.zookeeper.children.forPath(patchByVersion).await().map { name ->
                val path = "$patchByVersion/$name"
                async {
                    val mtime = node.zookeeper.checkExists().forPath(path).await().mtime
                    path to mtime
                }
            }
        }.awaitAll().sortedBy { it.second }
        patches.forEach { (path, _) ->
            val jarBytes = node.zookeeper.data.forPath(path).await()
            val message = Input(GZIPInputStream(jarBytes.inputStream())).use {
                kryo.use { readClassAndObject(it) }
            }
            node.scriptActor.tell(message)
            logger.info("apply patch $path -> $message")
        }
    }
}