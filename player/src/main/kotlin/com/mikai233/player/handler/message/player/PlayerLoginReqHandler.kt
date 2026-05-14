package com.mikai233.player.handler.message.player

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.common.extension.decodeActorRef
import com.mikai233.common.extension.tell
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.common.runtime.system
import com.mikai233.player.PlayerHandlerContext
import com.mikai233.player.PlayerMessageHandler
import com.mikai233.protocol.ProtoLogin
import com.mikai233.protocol.ProtoRpcPlayer.*
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.PROTOBUF)
class PlayerLoginReqHandler : PlayerMessageHandler<PlayerLoginReq> {
    override fun handle(context: PlayerHandlerContext, message: PlayerLoginReq) {
        val actor = context.actor
        actor.bindChannelActor(message.channelActor.decodeActorRef(actor.node.system))
        val response = actor.node.loginService.loginSuccessResp(actor)
        actor.sender.tell(PlayerLoginResp.newBuilder().setResponse(response.toRpcLoginResp()).build())
        actor.node.chatService.subscribeCurrentAllianceTopic(actor)
        actor.node.chatService.deliverOfflinePrivateMessages(actor)
        actor.self tell PlayerLoginEvent
    }

    private fun ProtoLogin.LoginResp.toRpcLoginResp(): RpcLoginResp {
        return RpcLoginResp.newBuilder()
            .setResult(result.toRpcLoginResult())
            .setData(
                RpcPlayerData.newBuilder()
                    .setPlayerId(data.playerId)
                    .setNickname(data.nickname)
                    .build(),
            )
            .setServerPublicKey(serverPublicKey)
            .setServerZone(serverZone)
            .build()
    }

    private fun ProtoLogin.LoginResult.toRpcLoginResult(): RpcLoginResult {
        return when (this) {
            ProtoLogin.LoginResult.Success -> RpcLoginResult.RpcLoginSuccess
            ProtoLogin.LoginResult.RegisterLimit -> RpcLoginResult.RpcLoginRegisterLimit
            ProtoLogin.LoginResult.WorldNotExists -> RpcLoginResult.RpcLoginWorldNotExists
            ProtoLogin.LoginResult.WorldClosed -> RpcLoginResult.RpcLoginWorldClosed
            ProtoLogin.LoginResult.AccountBan -> RpcLoginResult.RpcLoginAccountBan
            ProtoLogin.LoginResult.UNRECOGNIZED -> RpcLoginResult.UNRECOGNIZED
        }
    }
}
