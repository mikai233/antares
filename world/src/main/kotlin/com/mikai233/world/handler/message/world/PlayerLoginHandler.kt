package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.encodeActorRef
import com.mikai233.common.message.DispatcherKeys
import com.mikai233.common.message.GatewayRoutes
import com.mikai233.common.runtime.system
import com.mikai233.protocol.ProtoLogin
import com.mikai233.protocol.ProtoLogin.LoginReq
import com.mikai233.protocol.ProtoPlayer
import com.mikai233.protocol.ProtoRpcPlayer.*
import com.mikai233.world.WorldHandlerContext
import com.mikai233.world.WorldMessageHandler
import com.mikai233.world.data.PlayerAbstractMem
import com.mikai233.world.entity.PlayerAbstract
import io.github.realmlabs.asteria.message.AsteriaGatewayRoute
import io.github.realmlabs.asteria.message.AsteriaMessageHandler

@AllOpen
@AsteriaMessageHandler(dispatcher = DispatcherKeys.PROTOBUF)
@AsteriaGatewayRoute(route = GatewayRoutes.WORLD)
class PlayerLoginHandler : WorldMessageHandler<LoginReq> {
    override fun handle(context: WorldHandlerContext, message: LoginReq) {
        val actor = context.actor
        val channelActor = actor.sender
        val abstractMem = actor.manager.get<PlayerAbstractMem>()
        val playerAbstract = abstractMem.getByAccount(message.account)
        if (playerAbstract == null) {
            val playerId = actor.nextId()
            actor.sessionManager.createOrUpdateSession(playerId, channelActor)
            actor.launch {
                val response = actor.askPlayer<PlayerCreateResp>(
                    PlayerCreateReq.newBuilder()
                        .setAccount(message.account)
                        .setPlayerId(playerId)
                        .setWorldId(actor.worldId)
                        .setNickname("")
                        .setChannelActor(channelActor.encodeActorRef(actor.node.system))
                        .build(),
                ).getOrThrow()
                channelActor.tell(response.response.toClientLoginResp(), actor.self)
                val abstract = PlayerAbstract(
                    playerId,
                    actor.worldId,
                    message.account,
                    "",
                    1,
                    actor.gameTime.nowMillis(),
                )
                abstractMem.addAbstract(abstract)
            }
        } else {
            actor.sessionManager.createOrUpdateSession(playerAbstract.id, channelActor)
            actor.launch {
                val response = actor.askPlayer<PlayerLoginResp>(
                    PlayerLoginReq.newBuilder()
                        .setAccount(message.account)
                        .setPlayerId(playerAbstract.id)
                        .setWorldId(playerAbstract.worldId)
                        .setChannelActor(channelActor.encodeActorRef(actor.node.system))
                        .build(),
                ).getOrThrow()
                channelActor.tell(response.response.toClientLoginResp(), actor.self)
            }
        }
    }

    private fun RpcLoginResp.toClientLoginResp(): ProtoLogin.LoginResp {
        return ProtoLogin.LoginResp.newBuilder()
            .setResult(result.toClientLoginResult())
            .setData(
                ProtoPlayer.PlayerData.newBuilder()
                    .setPlayerId(data.playerId)
                    .setNickname(data.nickname)
                    .build(),
            )
            .setServerPublicKey(serverPublicKey)
            .setServerZone(serverZone)
            .build()
    }

    private fun RpcLoginResult.toClientLoginResult(): ProtoLogin.LoginResult {
        return when (this) {
            RpcLoginResult.RpcLoginSuccess -> ProtoLogin.LoginResult.Success
            RpcLoginResult.RpcLoginRegisterLimit -> ProtoLogin.LoginResult.RegisterLimit
            RpcLoginResult.RpcLoginWorldNotExists -> ProtoLogin.LoginResult.WorldNotExists
            RpcLoginResult.RpcLoginWorldClosed -> ProtoLogin.LoginResult.WorldClosed
            RpcLoginResult.RpcLoginAccountBan -> ProtoLogin.LoginResult.AccountBan
            RpcLoginResult.UNRECOGNIZED -> ProtoLogin.LoginResult.UNRECOGNIZED
        }
    }
}
