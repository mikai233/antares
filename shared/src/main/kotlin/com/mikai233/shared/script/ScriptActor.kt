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
import com.mikai233.common.ext.actorLogger
import com.mikai233.shared.message.*
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
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
                    is NodeRoleScript -> handleNodeRoleScript(message)
                    is NodeScript -> handleNodeScript(message)
                    is PlayerActorScript -> handlePlayerActorScript(message)
                    is WorldActorScript -> handleWorldActorScript(message)
                }
            } catch (throwable: Throwable) {
                logger.error("{} handle {}", selfMember.address(), message, throwable)
            }
            Behaviors.same()
        }.build()
    }

    private fun handleNodeRoleScript(message: NodeRoleScript) {
        val targetRole = message.role.name
        if (selfMember.hasRole(targetRole)) {
            handleNodeScript(NodeScript(message.name, message.type, message.body))
        } else {
            logger.error(
                "incorrect role script:{} route to member:{} with role:{}",
                targetRole,
                selfMember.address(),
                selfMember.roles
            )
        }
    }

    private fun handleNodeScript(message: NodeScript) {
        val script = instanceScript<NodeScriptFunction<in Launcher>>(message)
        script.invoke(node)
    }

    private fun handlePlayerActorScript(message: PlayerActorScript) {
        val script = instanceScript<ActorScriptFunction<AbstractBehavior<*>>>(message)
        message.replyTo.tell(ExecutePlayerScript(script))
    }

    private fun handleWorldActorScript(message: WorldActorScript) {
        val script = instanceScript<ActorScriptFunction<AbstractBehavior<*>>>(message)
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
        val file = when (script.type) {
            ScriptType.GroovyScript -> {
                File.createTempFile(script.name, ".groovy")
            }

            ScriptType.KotlinScript -> {
                File.createTempFile(script.name, ".jar")
            }
        }
        file.writeBytes(script.body)
        val jarfile = JarFile(file)
        val scriptName = jarfile.manifest.mainAttributes.getValue(ScriptClassName)
        val loader = URLClassLoader(arrayOf(file.toURI().toURL()))
        return loader.loadClass(scriptName).kotlin
    }

    private fun <T> instanceScript(script: Script): T {
        val scriptClass = loadClassWithCache(script)
        val constructor = requireNotNull(scriptClass.primaryConstructor) { "$scriptClass primaryConstructor not found" }
        @Suppress("UNCHECKED_CAST")
        return constructor.call() as T
    }
}