package com.mikai233.player.handler.protocol.test

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.annotation.AsteriaGatewayRoute
import com.mikai233.common.annotation.AsteriaMessageHandler
import com.mikai233.common.message.catalog.CatalogDispatcherKind
import com.mikai233.common.message.catalog.GatewayEntityIdSource
import com.mikai233.common.message.catalog.GatewayRouteTarget
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.protocol.ProtoTest.TestReq
import com.mikai233.protocol.testResp

@AllOpen
@AsteriaMessageHandler(CatalogDispatcherKind.PROTOBUF)
@AsteriaGatewayRoute(
    target = GatewayRouteTarget.PLAYER_ENTITY,
    entityIdSource = GatewayEntityIdSource.SESSION_PLAYER_ID,
    injectRouteEntityIdTo = ["player_id"],
)
class TestReqHandler : PlayerMessageHandler<TestReq> {
    override fun handle(context: PlayerHandlerContext, message: TestReq) {
        val actor = context.actor
        actor.send(testResp { })
    }
}
