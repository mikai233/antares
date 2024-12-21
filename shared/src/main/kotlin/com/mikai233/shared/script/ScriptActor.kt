package com.mikai233.shared.script

import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.cluster.Member
import akka.cluster.typed.Cluster
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.Launcher
import com.mikai233.common.extension.actorLogger
import com.mikai233.shared.message.*
import groovy.lang.GroovyClassLoader
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class ScriptActor(context: ActorContext<ScriptMessage>, private val node: Launcher) :
    AbstractBehavior<ScriptMessage>(context) {
    private val logger = actorLogger()
    private val selfMember: Member
    private val classCache: Cache<String, KClass<*>> = Caffeine.newBuilder()
        .maximumSize(20L)
        .expireAfterWrite(10.minutes.toJavaDuration())
        .build()

    companion object {
        const val ScriptClassName = "Script-Class"
        fun name() = "scriptActor@${GlobalEnv.machineIp}"
        fun path() = "/user/${name()}"
    }

    init {
        logger.info("{} started", context.self)
        selfMember = Cluster.get(context.system).selfMember()
    }

    override fun createReceive(): Receive<ScriptMessage> {
        return newReceiveBuilder().onMessage(ScriptMessage::class.java) { message ->
            logger.info("{} handle {}", selfMember.address(), message)
            try {
                when (message) {
                    is ExecuteNodeRoleScript -> handleNodeRoleScript(message)
                    is ExecuteNodeScript -> handleNodeScript(message)
                    is CompilePlayerActorScript -> handlePlayerActorScript(message)
                    is CompileWorldActorScript -> handleWorldActorScript(message)
                }
            } catch (throwable: Throwable) {
                logger.error("{} handle {}", selfMember.address(), message, throwable)
            }
            Behaviors.same()
        }.build()
    }

    private fun handleNodeRoleScript(message: ExecuteNodeRoleScript) {
        val targetRole = message.role.name
        if (selfMember.hasRole(targetRole)) {
            val script = instanceScript<NodeRoleScriptFunction<in Launcher>>(message.script)
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
        val script = instanceScript<NodeScriptFunction<in Launcher>>(message.script)
        script.invoke(node)
    }

    private fun handlePlayerActorScript(message: CompilePlayerActorScript) {
        val script = instanceScript<ActorScriptFunction<AbstractBehavior<*>>>(message.script)
        message.replyTo.tell(ExecutePlayerScript(script))
    }

    private fun handleWorldActorScript(message: CompileWorldActorScript) {
        val script = instanceScript<ActorScriptFunction<AbstractBehavior<*>>>(message.script)
        message.replyTo.tell(ExecuteWorldScript(script))
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
                val scriptName = jarfile.manifest.mainAttributes.getValue(ScriptClassName)
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
