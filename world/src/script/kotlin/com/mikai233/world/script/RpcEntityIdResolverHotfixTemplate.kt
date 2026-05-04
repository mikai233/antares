package com.mikai233.world.script

import com.mikai233.common.extension.logger
import com.mikai233.common.rpc.installRpcEntityIdFieldOverrides
import com.mikai233.world.WorldNode
import io.github.realmlabs.asteria.script.NodeScript
import io.github.realmlabs.asteria.script.NodeScriptContext
import io.github.realmlabs.asteria.script.ScriptExecutionResult

class RpcEntityIdResolverHotfixTemplate : NodeScript<WorldNode>() {
    private val logger = logger()

    override fun executeNode(context: NodeScriptContext<WorldNode>): ScriptExecutionResult? {
        context.runtime.installRpcEntityIdFieldOverrides(
            worldFieldOverrides = mapOf(
                // "com.mikai233.protocol.ProtoRpcWorld.WorldWakeupReq" to "world_id",
            ),
            playerFieldOverrides = mapOf(
                // "com.mikai233.protocol.ProtoRpcPlayer.PlayerLoginReq" to "player_id",
            ),
        )
        logger.info("rpc entity-id resolver hotfix installed on world node")
        return null
    }
}
