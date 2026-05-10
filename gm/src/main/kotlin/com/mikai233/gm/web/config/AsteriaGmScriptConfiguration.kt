package com.mikai233.gm.web.config

import com.mikai233.common.annotation.AllOpen
import com.mikai233.gm.GmNode
import io.github.realmlabs.asteria.cluster.pekko.EntityShardRegistry
import io.github.realmlabs.asteria.cluster.pekko.SingletonActorRegistry
import io.github.realmlabs.asteria.gm.core.AllowAllGmAuthorizationPolicy
import io.github.realmlabs.asteria.gm.core.GmAuthorizationPolicy
import io.github.realmlabs.asteria.gm.core.GmPrincipal
import io.github.realmlabs.asteria.gm.spring.GmPrincipalResolver
import io.github.realmlabs.asteria.gm.spring.HeaderGmPrincipalResolver
import io.github.realmlabs.asteria.script.ScriptRuntime
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@AllOpen
@Configuration
class AsteriaGmScriptConfiguration {
    @Bean
    fun asteriaScriptRuntime(node: GmNode): ScriptRuntime {
        return node.services.get(ScriptRuntime::class)
    }

    @Bean
    fun gmPrincipalResolver(): GmPrincipalResolver {
        val headerResolver = HeaderGmPrincipalResolver()
        return GmPrincipalResolver { request ->
            headerResolver.resolve(request) ?: GmPrincipal(
                id = "local-dev",
                displayName = "Local Dev",
            )
        }
    }

    @Bean
    fun gmAuthorizationPolicy(): GmAuthorizationPolicy {
        return AllowAllGmAuthorizationPolicy
    }

    @Bean
    fun entityShardRegistry(node: GmNode): EntityShardRegistry {
        return node.services.get(EntityShardRegistry::class)
    }

    @Bean
    fun singletonActorRegistry(node: GmNode): SingletonActorRegistry {
        return node.services.get(SingletonActorRegistry::class)
    }
}
