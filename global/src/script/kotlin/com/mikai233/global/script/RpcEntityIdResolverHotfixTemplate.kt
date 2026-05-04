package com.mikai233.global.script

import com.mikai233.common.extension.logger
import com.mikai233.common.rpc.installRpcEntityIdFieldOverrides
import com.mikai233.global.GlobalNode
import io.github.realmlabs.asteria.script.NodeScript
import io.github.realmlabs.asteria.script.NodeScriptContext
import io.github.realmlabs.asteria.script.ScriptExecutionResult

class RpcEntityIdResolverHotfixTemplate : NodeScript<GlobalNode>() {
    private val logger = logger()

    override fun executeNode(context: NodeScriptContext<GlobalNode>): ScriptExecutionResult? {
        context.runtime.installRpcEntityIdFieldOverrides(
            worldFieldOverrides = mapOf(
                // "com.mikai233.protocol.ProtoRpcWorld.WorldWakeupReq" to "world_id",
            ),
            playerFieldOverrides = mapOf(
                // "com.mikai233.protocol.ProtoRpcPlayer.PlayerLoginReq" to "player_id",
            ),
        )
        logger.info("rpc entity-id resolver hotfix installed on global node")
        return null
    }
}
