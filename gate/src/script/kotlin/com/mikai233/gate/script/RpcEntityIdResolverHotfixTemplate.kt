package com.mikai233.gate.script

import com.mikai233.common.extension.logger
import com.mikai233.common.rpc.installRpcEntityIdFieldOverrides
import com.mikai233.gate.GateNode
import io.github.realmlabs.asteria.script.NodeScript
import io.github.realmlabs.asteria.script.NodeScriptContext
import io.github.realmlabs.asteria.script.ScriptExecutionResult

class RpcEntityIdResolverHotfixTemplate : NodeScript<GateNode>() {
    private val logger = logger()

    override fun executeNode(context: NodeScriptContext<GateNode>): ScriptExecutionResult? {
        context.runtime.installRpcEntityIdFieldOverrides(
            worldFieldOverrides = mapOf(
                // "com.mikai233.protocol.ProtoRpcWorld.WorldWakeupReq" to "world_id",
            ),
            playerFieldOverrides = mapOf(
                // "com.mikai233.protocol.ProtoRpcPlayer.PlayerLoginReq" to "player_id",
            ),
        )
        logger.info("rpc entity-id resolver hotfix installed on gate node")
        return null
    }
}
