package com.mikai233.common.core

import io.github.realmlabs.asteria.core.AsteriaApplication
import io.github.realmlabs.asteria.starter.clusterGameApplication

internal object ConfigCenterGameClusterApplicationFactory : GameClusterApplicationFactory {
    override fun build(request: GameClusterApplicationRequest): AsteriaApplication {
        return clusterGameApplication(nodeId = request.nodeId, pekkoConfig = request.runtimeConfig()) {
            installGameNodeModules(request)
        }
    }
}
