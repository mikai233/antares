package com.mikai233.global.patch

import com.mikai233.common.rpc.FieldOverrideRpcEntityIdResolver
import com.mikai233.common.rpc.RpcEntityIdResolver
import com.mikai233.global.GamePatchBindings
import io.github.realmlabs.asteria.patch.RuntimePatchInstallContext
import io.github.realmlabs.asteria.patch.RuntimePatchPlugin

class RpcEntityIdResolverHotfixPatch : RuntimePatchPlugin {
    override suspend fun install(context: RuntimePatchInstallContext) {
        val bindings = context.runtime.services.get<GamePatchBindings>()
        val current = bindings.services.require(RpcEntityIdResolver::class)
        context.services.replace(
            bindings.services,
            RpcEntityIdResolver::class,
            FieldOverrideRpcEntityIdResolver(
                delegate = current,
                worldFieldOverrides = mapOf(
                    // "com.mikai233.protocol.ProtoRpcWorld.WorldWakeupReq" to "world_id",
                ),
                playerFieldOverrides = mapOf(
                    // "com.mikai233.protocol.ProtoRpcPlayer.PlayerLoginReq" to "player_id",
                ),
            ),
        )
    }
}
