package com.mikai233.common.runtime

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.realmlabs.asteria.cluster.pekko.*
import io.github.realmlabs.asteria.core.AsteriaApplication
import io.github.realmlabs.asteria.core.ModuleContext
import io.github.realmlabs.asteria.core.RoleKey
import io.github.realmlabs.asteria.core.gameApplication
import io.github.realmlabs.asteria.starter.GameServerStartupSummaryModule
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap
import org.apache.pekko.management.scaladsl.PekkoManagement
import java.net.InetSocketAddress

internal object KubernetesGameClusterApplicationFactory : GameClusterApplicationFactory {
    override fun build(request: GameClusterApplicationRequest): AsteriaApplication {
        return gameApplication {
            installGameNodeModules(request)
            install(PekkoRuntimeModule(KubernetesPekkoClusterStartup(request.runtimeConfig(), request.addr)))
            install(PekkoClusterConfigControlModule())
            install(GameServerStartupSummaryModule("kubernetes"))
        }
    }
}

private class KubernetesPekkoClusterStartup(
    private val fallbackConfig: Config,
    private val addr: InetSocketAddress,
) : PekkoClusterStartup {
    override suspend fun resolve(context: ModuleContext): PekkoClusterStartPlan {
        val roles = context.runtime.roles
        val config = ConfigFactory.parseMap(kubernetesConfig(context, roles))
            .withFallback(fallbackConfig)
            .withFallback(ConfigFactory.load())
        return PekkoClusterStartPlan(
            config = config,
            roles = roles,
            join = PekkoClusterJoin.Bootstrap,
        )
    }

    override suspend fun afterActorSystemCreated(
        context: ModuleContext,
        system: ActorSystem,
        plan: PekkoClusterStartPlan,
    ) {
        PekkoManagement.get(system).start()
        ClusterBootstrap.get(system).start()
    }

    private fun kubernetesConfig(context: ModuleContext, roles: Set<RoleKey>): Map<String, Any> {
        val managementPort = intEnv("PEKKO_MANAGEMENT_PORT") ?: addr.port + 2000
        val requiredContactPoints = intEnv("PEKKO_CLUSTER_BOOTSTRAP_REQUIRED_CONTACT_POINT_NR") ?: 1
        val serviceName = System.getenv("PEKKO_CLUSTER_BOOTSTRAP_SERVICE_NAME") ?: context.name
        val podLabelSelector = System.getenv("PEKKO_DISCOVERY_POD_LABEL_SELECTOR") ?: "app=%s"
        return mapOf(
            "pekko.actor.provider" to "cluster",
            "pekko.remote.artery.canonical.hostname" to addr.hostString,
            "pekko.remote.artery.canonical.port" to addr.port,
            "pekko.remote.artery.bind.hostname" to "0.0.0.0",
            "pekko.remote.artery.bind.port" to addr.port,
            "pekko.cluster.roles" to roles.map { it.value }.sorted(),
            "pekko.discovery.method" to "kubernetes-api",
            "pekko.discovery.kubernetes-api.use-raw-ip" to true,
            "pekko.discovery.kubernetes-api.pod-label-selector" to podLabelSelector,
            "pekko.management.http.hostname" to addr.hostString,
            "pekko.management.http.port" to managementPort,
            "pekko.management.http.bind-hostname" to "0.0.0.0",
            "pekko.management.http.bind-port" to managementPort,
            "pekko.management.cluster.bootstrap.contact-point-discovery.service-name" to serviceName,
            "pekko.management.cluster.bootstrap.contact-point-discovery.required-contact-point-nr" to
                    requiredContactPoints,
            "pekko.management.cluster.bootstrap.contact-point-discovery.port-name" to "management",
            "pekko.management.cluster.bootstrap.contact-point-discovery.protocol" to "tcp",
        )
    }

    private fun intEnv(name: String): Int? {
        return System.getenv(name)?.takeIf(String::isNotBlank)?.toInt()
    }
}
