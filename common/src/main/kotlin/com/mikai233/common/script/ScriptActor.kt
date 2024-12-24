package com.mikai233.common.script

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.Member
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.Node
import com.mikai233.common.extension.actorLogger
import com.mikai233.common.message.CompileScript
import com.mikai233.common.message.ExecuteNodeRoleScript
import com.mikai233.common.message.ExecuteNodeScript
import groovy.lang.GroovyClassLoader
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class ScriptActor(private val node: Node) : AbstractActor() {
    private val logger = actorLogger()
    private val selfMember: Member
    private val classCache: Cache<String, KClass<*>> = Caffeine.newBuilder()
        .maximumSize(20L)
        .expireAfterWrite(10.minutes.toJavaDuration())
        .build()

    companion object {
        const val SCRIPT_CLASS_NAME = "Script-Class"
        fun name() = "scriptActor@${GlobalEnv.machineIp}"
        fun path() = "/user/${name()}"

        fun props(node: Node): Props {
            return Props.create(ScriptActor::class.java) { ScriptActor(node) }
        }
    }

    init {
        logger.info("{} started", context.self)
        selfMember = Cluster.get(context.system).selfMember()
    }


    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(ExecuteNodeRoleScript::class.java) { handleNodeRoleScript(it) }
            .match(ExecuteNodeScript::class.java) { handleNodeScript(it) }
            .match(CompileScript::class.java) { handleCompileScript(it) }
            .build()
    }

    private fun handleNodeRoleScript(message: ExecuteNodeRoleScript) {
        val targetRole = message.role.name
        if (selfMember.hasRole(targetRole)) {
            val script = instanceScript<NodeRoleScriptFunction<in Node>>(message.script)
            script.invoke(node)
        } else {
            logger.error(
                "incorrect role script:{} route to member:{} with role:{}",
                targetRole,
                selfMember.address(),
                selfMember.roles
            )
        }
    }

    private fun handleNodeScript(message: ExecuteNodeScript) {
        val script = instanceScript<NodeScriptFunction<in Node>>(message.script)
        script.invoke(node)
    }

    private fun handleCompileScript(message: CompileScript) {
        val script = instanceScript<ActorScriptFunction<AbstractActor>>(message.script)
        sender.tell(ExecuteScript(script), ActorRef.noSender())
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

            ScriptType.KotlinScript -> {
                val file = File.createTempFile(script.name, ".jar")
                file.writeBytes(script.body)
                val jarfile = JarFile(file)
                val scriptName = jarfile.manifest.mainAttributes.getValue(SCRIPT_CLASS_NAME)
                val loader = URLClassLoader(arrayOf(file.toURI().toURL()))
                loader.loadClass(scriptName).kotlin
            }
        }
    }

    private fun <T> instanceScript(script: Script): T {
        val scriptClass = loadClassWithCache(script)
        val constructor =
            requireNotNull(scriptClass.constructors.find { it.parameters.isEmpty() }) { "$scriptClass empty constructor not found" }
        @Suppress("UNCHECKED_CAST")
        return constructor.call() as T
    }
}
