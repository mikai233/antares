package com.mikai233.common.runtime

import com.mikai233.common.runtime.module.PekkoCoroutineScopeModule
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.realmlabs.asteria.core.AsteriaApplication
import io.github.realmlabs.asteria.core.AsteriaApplicationBuilder
import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.NodeRuntime
import io.github.realmlabs.asteria.script.engine.groovy.GroovyScriptEngine
import io.github.realmlabs.asteria.script.engine.jar.JarScriptEngine
import io.github.realmlabs.asteria.script.pekko.ScriptModule
import java.net.InetSocketAddress

internal data class GameClusterApplicationRequest(
    val runtime: NodeRuntime,
    val addr: InetSocketAddress,
    val nodeId: String,
    val config: Config,
    val sameJvm: Boolean,
    val commonModules: List<AsteriaModule>,
    val beforeClusterModules: List<AsteriaModule>,
    val afterClusterModules: List<AsteriaModule>,
    val configure: AsteriaApplicationBuilder.() -> Unit,
) {
    fun runtimeConfig(): Config {
        return if (sameJvm) {
            ConfigFactory.parseMap(
                mapOf("pekko.cluster.jmx.multi-mbeans-in-same-jvm" to "on"),
            ).withFallback(config)
        } else {
            config
        }
    }
}

internal interface GameClusterApplicationFactory {
    fun build(request: GameClusterApplicationRequest): AsteriaApplication
}

internal object GameClusterApplicationFactories {
    fun select(config: Config): GameClusterApplicationFactory {
        val configuredMode = if (config.hasPath("game.cluster.discovery")) {
            config.getString("game.cluster.discovery")
        } else {
            null
        }
        val raw = System.getenv("CLUSTER_DISCOVERY") ?: configuredMode ?: "config-center"
        return when (raw.lowercase()) {
            "kubernetes", "k8s" -> KubernetesGameClusterApplicationFactory
            "config-center", "zookeeper", "zk", "topology" -> ConfigCenterGameClusterApplicationFactory
            else -> error("Unsupported cluster discovery mode: $raw")
        }
    }
}

internal fun AsteriaApplicationBuilder.installGameNodeModules(request: GameClusterApplicationRequest) {
    name = request.runtime.name
    request.commonModules.forEach(::install)
    request.beforeClusterModules.forEach(::install)
    request.configure(this)
    install(PekkoCoroutineScopeModule())
    install(
        ScriptModule {
            engine(GroovyScriptEngine())
            engine(JarScriptEngine())
            allowNodeScripts = true
            allowActorScripts = true
        },
    )
    request.afterClusterModules.forEach(::install)
}
