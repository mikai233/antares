package com.mikai233.player.patch

import com.mikai233.common.extension.logger
import com.mikai233.player.GamePatchBindings
import com.mikai233.player.PlayerActor
import com.mikai233.player.service.LoginService
import com.mikai233.protocol.ProtoRpcPlayer.PlayerCreateReq
import io.github.realmlabs.asteria.patch.RuntimePatchInstallContext
import io.github.realmlabs.asteria.patch.RuntimePatchPlugin

class LoginServiceHotfixPatch : RuntimePatchPlugin {
    override suspend fun install(context: RuntimePatchInstallContext) {
        val bindings = context.runtime.services.get<GamePatchBindings>()
        context.services.replace(
            bindings.services,
            LoginService::class,
            LoginServiceHotfix(),
        )
    }
}

class LoginServiceHotfix : LoginService() {
    private val logger = logger()

    override fun createPlayer(player: PlayerActor, playerCreateReq: PlayerCreateReq) {
        logger.info("fix logic")
        super.createPlayer(player, playerCreateReq)
    }
}
