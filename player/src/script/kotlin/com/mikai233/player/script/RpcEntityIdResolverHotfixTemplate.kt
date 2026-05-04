package com.mikai233.player.script

import com.mikai233.common.extension.logger
import com.mikai233.common.rpc.installRpcEntityIdFieldOverrides
import com.mikai233.player.PlayerNode
import io.github.realmlabs.asteria.script.NodeScript
import io.github.realmlabs.asteria.script.NodeScriptContext
import io.github.realmlabs.asteria.script.ScriptExecutionResult

/**
 * Temporary hotfix template for missing internal protobuf shard routing metadata.
 *
 * Apply the same override on every node role that uses the relevant shard extractor
 * (`gate`, `player`, `world`, `global`) until the proto option is fixed and deployed.
 */
class RpcEntityIdResolverHotfixTemplate : NodeScript<PlayerNode>() {
    private val logger = logger()

    override fun executeNode(context: NodeScriptContext<PlayerNode>): ScriptExecutionResult? {
        context.runtime.installRpcEntityIdFieldOverrides(
            // Full protobuf message class name -> protobuf field name
            worldFieldOverrides = mapOf(
                // "com.mikai233.protocol.ProtoRpcWorld.WorldWakeupReq" to "world_id",
            ),
            playerFieldOverrides = mapOf(
                // "com.mikai233.protocol.ProtoRpcPlayer.PlayerLoginReq" to "player_id",
            ),
        )
        logger.info("rpc entity-id resolver hotfix installed on player node")
        return null
    }
}
