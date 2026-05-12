package com.mikai233.common.runtime

import io.github.realmlabs.asteria.cluster.config.ClusterConfigLayout
import io.github.realmlabs.asteria.cluster.config.ClusterConfigModule
import io.github.realmlabs.asteria.cluster.pekko.PekkoClusterConfigControlModule
import io.github.realmlabs.asteria.cluster.pekko.PekkoRuntimeModule
import io.github.realmlabs.asteria.cluster.pekko.TopologyPekkoClusterStartup
import io.github.realmlabs.asteria.core.AsteriaApplication
import io.github.realmlabs.asteria.core.gameApplication
import io.github.realmlabs.asteria.starter.GameServerStartupSummaryModule

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
            install(PekkoRuntimeModule(TopologyPekkoClusterStartup(request.nodeId, config = request.runtimeConfig())))
            install(PekkoClusterConfigControlModule())
            install(GameServerStartupSummaryModule("config-center"))
        }
    }
}
