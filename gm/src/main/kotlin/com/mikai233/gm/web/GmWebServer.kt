package com.mikai233.gm.web

import com.mikai233.common.core.Patcher
import com.mikai233.gm.GmNode
import com.typesafe.config.Config
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.GenericApplicationContext

class GmWebServer(private val node: GmNode) {
    private lateinit var context: ConfigurableApplicationContext

    fun start() {
        context = SpringApplicationBuilder(GmWebApplication::class.java)
            .web(WebApplicationType.SERVLET)
            .properties(buildProperties())
            .initializers(
                ApplicationContextInitializer { applicationContext: GenericApplicationContext ->
                    applicationContext.beanFactory.registerSingleton("gmNode", node)
                    applicationContext.beanFactory.registerSingleton("scriptKryo", Patcher.scriptKryo())
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
            "server.port" to node.config.getIntOrDefault("gm.web.port", "ktor.deployment.port", 8080),
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
            "management.endpoints.web.exposure.include" to "health,info,metrics",
        )
    }
}

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
