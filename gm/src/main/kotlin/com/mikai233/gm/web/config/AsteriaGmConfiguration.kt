package com.mikai233.gm.web.config

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.runtime.system
import com.mikai233.gm.GmNode
import io.github.realmlabs.asteria.cluster.config.ClusterConfigControlService
import io.github.realmlabs.asteria.cluster.pekko.EntityShardRegistry
import io.github.realmlabs.asteria.cluster.pekko.SingletonActorRegistry
import io.github.realmlabs.asteria.config.ConfigReloadMonitor
import io.github.realmlabs.asteria.config.ConfigService
import io.github.realmlabs.asteria.config.center.ConfigStore
import io.github.realmlabs.asteria.gm.core.AllowAllGmAuthorizationPolicy
import io.github.realmlabs.asteria.gm.core.GmAuthorizationPolicy
import io.github.realmlabs.asteria.gm.core.GmPrincipal
import io.github.realmlabs.asteria.gm.cluster.GmClusterStatusService
import io.github.realmlabs.asteria.gm.cluster.pekko.PekkoGmClusterStatusService
import io.github.realmlabs.asteria.gm.spring.GmPrincipalResolver
import io.github.realmlabs.asteria.gm.spring.HeaderGmPrincipalResolver
import io.github.realmlabs.asteria.patch.PatchApplicationService
import io.github.realmlabs.asteria.patch.PatchClusterApplicationService
import io.github.realmlabs.asteria.patch.PatchNodeProvider
import io.github.realmlabs.asteria.patch.RuntimePatchRepository
import io.github.realmlabs.asteria.patch.WritablePatchArtifactStore
import io.github.realmlabs.asteria.script.ScriptRuntime
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@AllOpen
@Configuration
class AsteriaGmConfiguration {
    @Bean
    fun asteriaScriptRuntime(node: GmNode): ScriptRuntime {
        return node.services.get(ScriptRuntime::class)
    }

    @Bean
    fun configService(node: GmNode): ConfigService {
        return node.services.get(ConfigService::class)
    }

    @Bean
    fun configReloadMonitor(node: GmNode): ConfigReloadMonitor {
        return node.services.get(ConfigReloadMonitor::class)
    }

    @Bean
    fun clusterConfigControlService(node: GmNode): ClusterConfigControlService {
        return node.services.get(ClusterConfigControlService::class)
    }

    @Bean
    fun configStore(node: GmNode): ConfigStore {
        return node.services.get(ConfigStore::class)
    }

    @Bean
    fun gmClusterStatusService(node: GmNode): GmClusterStatusService {
        return PekkoGmClusterStatusService(node.system)
    }

    @Bean
    fun runtimePatchRepository(node: GmNode): RuntimePatchRepository {
        return node.services.get(RuntimePatchRepository::class)
    }

    @Bean
    fun patchApplicationService(node: GmNode): PatchApplicationService {
        return node.services.get(PatchApplicationService::class)
    }

    @Bean
    fun patchClusterApplicationService(node: GmNode): PatchClusterApplicationService {
        return node.services.get(PatchClusterApplicationService::class)
    }

    @Bean
    fun writablePatchArtifactStore(node: GmNode): WritablePatchArtifactStore {
        return node.services.get(WritablePatchArtifactStore::class)
    }

    @Bean
    fun patchNodeProvider(node: GmNode): PatchNodeProvider {
        return node.services.get(PatchNodeProvider::class)
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
