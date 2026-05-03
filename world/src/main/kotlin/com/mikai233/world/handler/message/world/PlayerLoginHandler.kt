package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.core.system
import com.mikai233.common.entity.PlayerAbstract
import com.mikai233.common.extension.unixTimestamp
import com.mikai233.common.message.requireActor
import com.mikai233.protocol.ProtoLogin.LoginReq
import com.mikai233.protocol.ProtoRpc.PlayerCreateReq
import com.mikai233.protocol.ProtoRpc.PlayerCreateResp
import com.mikai233.protocol.ProtoRpc.PlayerLoginReq
import com.mikai233.protocol.ProtoRpc.PlayerLoginResp
import com.mikai233.world.WorldActor
import com.mikai233.world.data.PlayerAbstractMem
import io.github.realmlabs.asteria.message.HandlerContext
import io.github.realmlabs.asteria.message.MessageHandler

@AllOpen
class PlayerLoginHandler : MessageHandler<LoginReq> {
    override fun handle(context: HandlerContext, message: LoginReq) {
        val actor = context.requireActor<WorldActor>()
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
                        .setChannelActorPath(channelActor.path().toStringWithAddress(actor.node.system.provider().defaultAddress))
                        .build(),
                ).getOrThrow()
                channelActor.tell(response.response, actor.self)
                val abstract = PlayerAbstract(playerId, actor.worldId, message.account, "", 1, unixTimestamp())
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
                        .setChannelActorPath(channelActor.path().toStringWithAddress(actor.node.system.provider().defaultAddress))
                        .build(),
                ).getOrThrow()
                channelActor.tell(response.response, actor.self)
            }
        }
    }
}
