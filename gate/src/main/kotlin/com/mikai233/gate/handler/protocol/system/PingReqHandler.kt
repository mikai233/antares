package com.mikai233.gate.handler.protocol.system

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.annotation.AsteriaGatewayRoute
import com.mikai233.common.extension.unixTimestamp
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.common.message.catalog.GatewayRouteTarget
import com.mikai233.gate.ChannelHandlerContext
import com.mikai233.gate.ChannelMessageHandler
import com.mikai233.protocol.ProtoSystem.PingReq
import com.mikai233.protocol.pingResp
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.PROTOBUF)
@AsteriaGatewayRoute(target = GatewayRouteTarget.GATEWAY_LOCAL)
class PingReqHandler : ChannelMessageHandler<PingReq> {
    override fun handle(context: ChannelHandlerContext, message: PingReq) {
        val actor = context.actor
        actor.write(pingResp { serverTimestamp = unixTimestamp() })
    }
}
