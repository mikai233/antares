package com.mikai233.gm.web.config

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.core.GameEntityKinds
import com.mikai233.common.core.GameRoles
import com.mikai233.common.core.GameSingletons
import com.mikai233.common.core.gameWorldIds
import com.mikai233.common.core.system
import com.mikai233.gm.GmNode
import io.github.realmlabs.asteria.core.EntityKind
import io.github.realmlabs.asteria.core.RoleKey
import io.github.realmlabs.asteria.core.SingletonName
import io.github.realmlabs.asteria.gm.core.GmFeatureRegistry
import io.github.realmlabs.asteria.gm.core.GmPrincipal
import io.github.realmlabs.asteria.gm.script.GmScriptTargetCatalog
import io.github.realmlabs.asteria.gm.spring.GmPrincipalResolver
import io.github.realmlabs.asteria.gm.spring.HeaderGmPrincipalResolver
import io.github.realmlabs.asteria.script.ScriptRuntime
import org.apache.pekko.cluster.Cluster
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
    fun gmPrincipalResolver(registry: GmFeatureRegistry): GmPrincipalResolver {
        val headerResolver = HeaderGmPrincipalResolver()
        val permissions = registry.permissions().mapTo(linkedSetOf()) { it.key }
        return GmPrincipalResolver { request ->
            headerResolver.resolve(request) ?: GmPrincipal(
                id = "local-dev",
                displayName = "Local Dev",
                roles = setOf("admin"),
                permissions = permissions,
            )
        }
    }

    @Bean
    fun gmScriptTargetCatalog(node: GmNode): GmScriptTargetCatalog {
        return object : GmScriptTargetCatalog {
            override suspend fun listRoles(): List<String> {
                return GameRoles.all.sorted()
            }

            override suspend fun listEntityKinds(): List<String> {
                return GameEntityKinds.all.sorted()
            }

            override suspend fun listSingletons(): List<String> {
                return GameSingletons.all.sorted()
            }

            override suspend fun listNodeAddresses(): List<String> {
                return nodeAddresses(node)
            }

            override suspend fun roleExists(role: RoleKey): Boolean {
                return role.value in GameRoles.all
            }

            override suspend fun entityKindExists(kind: EntityKind): Boolean {
                return kind.value in GameEntityKinds.all
            }

            override suspend fun entityIdExists(kind: EntityKind, id: String): Boolean? {
                return when (kind.value) {
                    GameEntityKinds.WorldActor -> id.toLongOrNull()?.let { it in node.gameWorldIds } ?: false
                    GameEntityKinds.PlayerActor -> null
                    else -> false
                }
            }

            override suspend fun singletonExists(name: SingletonName): Boolean {
                return name.value in GameSingletons.all
            }

            override suspend fun nodeAddressExists(address: String): Boolean {
                return address in nodeAddresses(node)
            }
        }
    }

    private fun nodeAddresses(node: GmNode): List<String> {
        return Cluster.get(node.system).state().members
            .map { it.address().toString() }
            .sorted()
    }
}
