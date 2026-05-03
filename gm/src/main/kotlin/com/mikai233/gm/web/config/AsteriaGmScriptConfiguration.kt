package com.mikai233.gm.web.config

import com.mikai233.common.core.Role
import com.mikai233.common.core.ShardEntityType
import com.mikai233.common.core.Singleton
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
                return Role.entries.map { it.name }.sorted()
            }

            override suspend fun listEntityKinds(): List<String> {
                return ShardEntityType.entries.map { it.name }.sorted()
            }

            override suspend fun listSingletons(): List<String> {
                return Singleton.entries.map { it.actorName }.sorted()
            }

            override suspend fun listNodeAddresses(): List<String> {
                return nodeAddresses(node)
            }

            override suspend fun roleExists(role: RoleKey): Boolean {
                return Role.entries.any { it.name == role.value }
            }

            override suspend fun entityKindExists(kind: EntityKind): Boolean {
                return ShardEntityType.entries.any { it.name == kind.value }
            }

            override suspend fun entityIdExists(kind: EntityKind, id: String): Boolean? {
                return when (kind.value) {
                    ShardEntityType.WorldActor.name -> id.toLongOrNull()?.let { it in node.gameWorldIds } ?: false
                    ShardEntityType.PlayerActor.name -> null
                    else -> false
                }
            }

            override suspend fun singletonExists(name: SingletonName): Boolean {
                return Singleton.entries.any { it.actorName == name.value }
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
