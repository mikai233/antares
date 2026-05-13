package com.mikai233.gm.web

import com.mikai233.common.config.DataSourceConfig
import com.mikai233.common.config.ROOT
import com.mikai233.common.config.SYSTEM_NAME
import com.mikai233.common.config.mongoUri
import com.mikai233.gm.GmNode
import com.typesafe.config.Config
import io.github.realmlabs.asteria.cluster.config.ClusterConfigLayout
import io.github.realmlabs.asteria.cluster.config.RuntimeNodeConfig
import io.github.realmlabs.asteria.config.center.RuntimeConfigRepository
import kotlinx.coroutines.runBlocking
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.GenericApplicationContext

class GmHttpServer(
    private val node: GmNode,
    private val dataSource: DataSourceConfig,
) {
    private lateinit var context: ConfigurableApplicationContext

    fun start() {
        context = SpringApplicationBuilder(GmHttpApplication::class.java)
            .web(WebApplicationType.SERVLET)
            .properties(buildProperties())
            .initializers(
                ApplicationContextInitializer { applicationContext: GenericApplicationContext ->
                    applicationContext.beanFactory.registerSingleton("gmNode", node)
                },
            )
            .run()
    }

    fun stop() {
        if (::context.isInitialized) {
            context.close()
        }
    }

    private fun buildProperties(): Map<String, Any> {
        return linkedMapOf(
            "spring.application.name" to "gm",
            "server.address" to node.config.getStringOrDefault("gm.web.host", "ktor.deployment.host", "0.0.0.0"),
            "server.port" to node.config.getIntOrDefault("gm.web.port", "ktor.deployment.port", 18080),
            "spring.servlet.multipart.max-file-size" to
                    node.config.getStringOrDefault(
                        "gm.web.multipart.max-file-size",
                        defaultValue = "128MB",
                    ),
            "spring.servlet.multipart.max-request-size" to
                    node.config.getStringOrDefault(
                        "gm.web.multipart.max-request-size",
                        defaultValue = "128MB",
                    ),
            "asteria.script.job.mongodb.uri" to dataSource.mongoUri(),
            "asteria.script.job.mongodb.database" to dataSource.databaseName,
            "asteria.script.job.mongodb.ensure-indexes" to true,
            "asteria.script.job.in-memory-repository-enabled" to false,
            "asteria.gm.config-center.allowed-roots[0]" to ROOT.value,
            "management.endpoints.web.exposure.include" to "health,info,metrics",
        ) + buildPekkoManagementProperties()
    }

    private fun buildPekkoManagementProperties(): Map<String, Any> {
        val endpoints = loadPekkoManagementEndpoints()
        return if (endpoints.isEmpty()) {
            mapOf(
                "asteria.gm.cluster.pekko-management.base-url" to
                        "http://${node.addr.hostString}:${node.addr.port + PEKKO_MANAGEMENT_PORT_OFFSET}",
            )
        } else {
            buildMap {
                endpoints.forEachIndexed { index, endpoint ->
                    put("asteria.gm.cluster.pekko-management.endpoints[$index].base-url", endpoint.baseUrl)
                    put("asteria.gm.cluster.pekko-management.endpoints[$index].node-address", endpoint.nodeAddress)
                }
            }
        }
    }

    private fun loadPekkoManagementEndpoints(): List<PekkoManagementEndpointConfig> {
        return runCatching {
            val repository = node.services.get(RuntimeConfigRepository::class)
            val layout = ClusterConfigLayout.default(SYSTEM_NAME)
            runBlocking {
                repository.children<RuntimeNodeConfig>(layout.nodes)
                    .values
                    .values
                    .map { it.value }
                    .sortedBy { it.nodeId }
                    .map { config ->
                        PekkoManagementEndpointConfig(
                            baseUrl = "http://${config.host}:${config.port + PEKKO_MANAGEMENT_PORT_OFFSET}",
                            nodeAddress = "pekko://${node.name}@${config.host}:${config.port}",
                        )
                    }
            }
        }.getOrDefault(emptyList())
    }
}

private data class PekkoManagementEndpointConfig(
    val baseUrl: String,
    val nodeAddress: String,
)

private const val PEKKO_MANAGEMENT_PORT_OFFSET: Int = 2000

private fun Config.getIntOrDefault(primaryPath: String, fallbackPath: String, defaultValue: Int): Int {
    return when {
        hasPath(primaryPath) -> getInt(primaryPath)
        hasPath(fallbackPath) -> getInt(fallbackPath)
        else -> defaultValue
    }
}

private fun Config.getStringOrDefault(primaryPath: String, fallbackPath: String? = null, defaultValue: String): String {
    return when {
        hasPath(primaryPath) -> getString(primaryPath)
        fallbackPath != null && hasPath(fallbackPath) -> getString(fallbackPath)
        else -> defaultValue
    }
}
