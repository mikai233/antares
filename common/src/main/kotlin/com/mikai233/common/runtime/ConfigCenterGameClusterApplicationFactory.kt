package com.mikai233.common.runtime

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.realmlabs.asteria.cluster.config.ClusterConfigLayout
import io.github.realmlabs.asteria.cluster.config.ClusterConfigModule
import io.github.realmlabs.asteria.cluster.pekko.*
import io.github.realmlabs.asteria.core.AsteriaApplication
import io.github.realmlabs.asteria.core.ModuleContext
import io.github.realmlabs.asteria.core.gameApplication
import io.github.realmlabs.asteria.starter.GameServerStartupSummaryModule
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.management.scaladsl.PekkoManagement
import java.net.InetSocketAddress

internal object ConfigCenterGameClusterApplicationFactory : GameClusterApplicationFactory {
    override fun build(request: GameClusterApplicationRequest): AsteriaApplication {
        return gameApplication {
            installGameNodeModules(request)
            val applicationName = name
            install(
                ClusterConfigModule {
                    layout = ClusterConfigLayout.default(applicationName)
                },
            )
            install(
                PekkoRuntimeModule(
                    ConfigCenterPekkoClusterStartup(
                        nodeId = request.nodeId,
                        config = request.runtimeConfig(),
                        addr = request.addr,
                    ),
                ),
            )
            install(PekkoClusterConfigControlModule())
            install(GameServerStartupSummaryModule("config-center"))
        }
    }
}

private class ConfigCenterPekkoClusterStartup(
    nodeId: String,
    config: Config,
    addr: InetSocketAddress,
) : PekkoClusterStartup {
    private val delegate = TopologyPekkoClusterStartup(
        nodeId = nodeId,
        config = managementConfig(addr).withFallback(config),
    )

    override suspend fun resolve(context: ModuleContext): PekkoClusterStartPlan {
        return delegate.resolve(context)
    }

    override suspend fun afterActorSystemCreated(
        context: ModuleContext,
        system: ActorSystem,
        plan: PekkoClusterStartPlan,
    ) {
        PekkoManagement.get(system).start()
    }

    private fun managementConfig(addr: InetSocketAddress): Config {
        val managementPort = addr.port + PEKKO_MANAGEMENT_PORT_OFFSET
        return ConfigFactory.parseMap(
            mapOf(
                "pekko.cluster.app-version" to versionText(),
                "pekko.management.http.hostname" to addr.hostString,
                "pekko.management.http.port" to managementPort,
                "pekko.management.http.bind-hostname" to "0.0.0.0",
                "pekko.management.http.bind-port" to managementPort,
                "pekko.management.http.route-providers-read-only" to false,
            ),
        )
    }
}

private const val PEKKO_MANAGEMENT_PORT_OFFSET: Int = 2000
