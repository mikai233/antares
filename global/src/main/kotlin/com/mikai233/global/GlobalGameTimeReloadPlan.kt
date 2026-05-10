package com.mikai233.global

import com.mikai233.common.extension.ask
import com.mikai233.common.runtime.RestartSingletonChild
import com.mikai233.common.runtime.SingletonChildRestarted
import com.mikai233.common.runtime.StartupLikeReloadPlan
import com.mikai233.common.runtime.StartupLikeReloadResult
import kotlin.time.Duration.Companion.seconds

class GlobalGameTimeReloadPlan(
    private val node: GlobalNode,
) : StartupLikeReloadPlan {
    override suspend fun reload(planId: String): StartupLikeReloadResult {
        val response = node.workerActor.ask<SingletonChildRestarted>(
            RestartSingletonChild(planId),
            timeout = 30.seconds,
        ).getOrThrow()
        check(response.planId == planId) {
            "unexpected worker restart response planId=${response.planId}, expected=$planId"
        }
        return StartupLikeReloadResult(stoppedActors = 1)
    }
}
