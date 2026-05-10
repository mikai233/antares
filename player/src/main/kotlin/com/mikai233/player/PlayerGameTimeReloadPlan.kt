package com.mikai233.player

import com.mikai233.common.runtime.GameEntityKinds
import com.mikai233.common.runtime.StartupLikeReloadPlan
import com.mikai233.common.runtime.StartupLikeReloadResult
import com.mikai233.common.runtime.localEntityRegistry
import com.mikai233.common.runtime.stopForReload
import com.mikai233.player.message.HandoffPlayer
import kotlin.time.Duration.Companion.seconds

class PlayerGameTimeReloadPlan(
    private val node: PlayerNode,
) : StartupLikeReloadPlan {
    override suspend fun reload(planId: String): StartupLikeReloadResult {
        return node.localEntityRegistry.stopForReload(
            kind = GameEntityKinds.PlayerActor,
            stopMessage = HandoffPlayer,
            timeout = 30.seconds,
        )
    }
}
