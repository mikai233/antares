package com.mikai233.player.handler.protocol.battle

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.common.message.GatewayRoutes
import com.mikai233.common.runtime.battleControlClient
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.protocol.ProtoBattle.BattleStartReq
import com.mikai233.protocol.ProtoBattle.BattleStartResp
import com.mikai233.protocol.ProtoBattle.BattleStartResult
import io.github.realmlabs.asteria.message.AsteriaGatewayRoute
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.PROTOBUF)
@AsteriaGatewayRoute(route = GatewayRoutes.PLAYER)
class BattleStartReqHandler : PlayerMessageHandler<BattleStartReq> {
    override fun handle(context: PlayerHandlerContext, message: BattleStartReq) {
        val actor = context.actor
        if (message.playerId != 0L && message.playerId != actor.playerId) {
            actor.send(
                BattleStartResp.newBuilder()
                    .setClientSeq(message.clientSeq)
                    .setResult(BattleStartResult.BattleStartRejected)
                    .setReason("battle start playerId does not match session")
                    .build(),
            )
            return
        }
        actor.send(
            actor.node.battleControlClient.startBattle(
                playerId = actor.playerId,
                mode = message.mode,
                clientSeq = message.clientSeq,
            ),
        )
    }
}
