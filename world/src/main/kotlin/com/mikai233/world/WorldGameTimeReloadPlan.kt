package com.mikai233.world

import com.mikai233.common.runtime.GameEntityKinds
import com.mikai233.common.runtime.StartupLikeReloadPlan
import com.mikai233.common.runtime.StartupLikeReloadResult
import com.mikai233.common.runtime.gameWorldIds
import com.mikai233.common.runtime.localEntityRegistry
import com.mikai233.common.runtime.stopForReload
import com.mikai233.common.runtime.module.WORLD_WAKE_TASK
import com.mikai233.world.message.HandoffWorld
import io.github.realmlabs.asteria.cluster.pekko.PekkoEntityWaker
import java.io.Serializable
import kotlin.time.Duration.Companion.seconds

class WorldGameTimeReloadPlan(
    private val node: WorldNode,
) : StartupLikeReloadPlan {
    override suspend fun reload(planId: String): StartupLikeReloadResult {
        val result = node.localEntityRegistry.stopForReload(
            kind = GameEntityKinds.WorldActor,
            stopMessage = HandoffWorld,
            timeout = 30.seconds,
        )
        node.services.find(PekkoEntityWaker::class)
            ?.wake(WORLD_WAKE_TASK, node.gameWorldIds.map { it as Serializable })
        return result
    }
}
