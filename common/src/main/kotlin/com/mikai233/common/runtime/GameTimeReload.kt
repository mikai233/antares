package com.mikai233.common.runtime

import kotlin.time.Duration

interface StartupLikeReloadPlan {
    suspend fun reload(planId: String): StartupLikeReloadResult
}

data class StartupLikeReloadResult(
    val stoppedActors: Int = 0,
    val failedActors: Int = 0,
)

object NoopStartupLikeReloadPlan : StartupLikeReloadPlan {
    override suspend fun reload(planId: String): StartupLikeReloadResult {
        return StartupLikeReloadResult()
    }
}

suspend fun LocalEntityRegistry.stopForReload(
    kind: String,
    stopMessage: Any,
    timeout: Duration,
    maxConcurrency: Int = 32,
): StartupLikeReloadResult {
    val result = stop(kind, stopMessage, timeout, maxConcurrency)
    check(result.failed == 0) {
        "failed to stop ${result.failed}/${result.entries.size} local $kind actors"
    }
    return StartupLikeReloadResult(
        stoppedActors = result.stopped,
        failedActors = result.failed,
    )
}
