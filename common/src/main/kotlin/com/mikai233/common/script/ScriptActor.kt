package com.mikai233.common.script

import akka.actor.AbstractActor
import akka.actor.Props
import akka.cluster.Cluster
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.mikai233.common.core.Node
import com.mikai233.common.extension.actorLogger
import com.mikai233.common.message.*
import groovy.lang.GroovyClassLoader
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class ScriptActor<N>(private val node: N) : AbstractActor() where N : Node {
    companion object {
        const val SCRIPT_CLASS_NAME = "Script-Class"
        const val NAME = "scriptActor"

        fun <N : Node> props(node: N): Props {
            return Props.create(ScriptActor::class.java) { ScriptActor(node) }
        }
    }

    private val logger = actorLogger()
    private val selfMember = Cluster.get(context.system).selfMember()
    private val classCache: Cache<String, KClass<*>> = Caffeine.newBuilder()
        .maximumSize(20L)
        .expireAfterWrite(10.minutes.toJavaDuration())
        .build()

    override fun preStart() {
        logger.info("{} started", self)
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(ExecuteNodeRoleScript::class.java) { handleNodeRoleScript(it) }
            .match(ExecuteNodeScript::class.java) { handleNodeScript(it) }
            .match(CompileActorScript::class.java) { handleCompileActorScript(it) }
            .build()
    }

    private fun handleNodeRoleScript(message: ExecuteNodeRoleScript) {
        if (message.filter.isNotEmpty() && selfMember.address() !in message.filter) {
            return
        }
        val targetRole = message.role.name
        if (selfMember.hasRole(targetRole)) {
            runCatching {
                val script = message.script
                val nodeRoleScriptFunction = scriptInstance<NodeRoleScriptFunction<N>>(script)
                nodeRoleScriptFunction.invoke(node, script.extra)
            }.onFailure {
                logger.error(it, "execute role script:{} on node:{} failed", targetRole, node::class.simpleName)
            }
        }
    }

    private fun handleNodeScript(message: ExecuteNodeScript) {
        if (message.filter.isNotEmpty() && selfMember.address() !in message.filter) {
            return
        }
        runCatching {
            val script = message.script
            val nodeScriptFunction = scriptInstance<NodeScriptFunction>(script)
            nodeScriptFunction.invoke(node, script.extra)
        }.onFailure {
            logger.error(it, "execute node script failed on node:{}", node::class.simpleName)
        }
    }

    private fun handleCompileActorScript(message: CompileActorScript) {
        runCatching {
            val script = message.script
            val actorScriptFunction = scriptInstance<ActorScriptFunction<in AbstractActor>>(script)
            message.actor.forward(ExecuteActorFunction(message.uid, actorScriptFunction, script.extra), context)
        }.onFailure {
            logger.error(it, "compile actor script failed")
            sender.tell(ExecuteScriptResult(message.uid, false), message.actor)
        }
    }

    private fun loadClassWithCache(script: Script): KClass<*> {
        val key = "${script.name}_${script.type.name}_${script.body.contentHashCode()}"
        val cacheClazz = classCache.getIfPresent(key)
        return if (cacheClazz == null) {
            val clazz = loadClass(script)
            classCache.put(key, clazz)
            clazz
        } else {
            cacheClazz
        }
    }

    private fun loadClass(script: Script): KClass<*> {
        return when (script.type) {
            ScriptType.GroovyScript -> {
                val text = String(script.body)
                GroovyClassLoader().parseClass(text).kotlin
            }

            ScriptType.JarScript -> {
                val file = File.createTempFile(script.name, ".jar")
                file.writeBytes(script.body)
                val jarFile = JarFile(file)
                val scriptName = requireNotNull(jarFile.manifest.mainAttributes.getValue(SCRIPT_CLASS_NAME)) {
                    "Script-Class not found in manifest"
                }
                val loader = URLClassLoader(arrayOf(file.toURI().toURL()))
                loader.loadClass(scriptName).kotlin
            }
        }
    }

    private fun <T> scriptInstance(script: Script): T {
        val scriptClass = loadClassWithCache(script)
        val constructor = requireNotNull(scriptClass.primaryConstructor) {
            "$scriptClass must have a empty primary constructor"
        }
        @Suppress("UNCHECKED_CAST")
        return constructor.call() as T
    }
}
