package com.mikai233.world.handler.message.world

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.entity.PlayerAbstract
import com.mikai233.common.extension.unixTimestamp
import com.mikai233.common.message.requireActor
import com.mikai233.common.message.player.PlayerCreateReq
import com.mikai233.common.message.player.PlayerCreateResp
import com.mikai233.common.message.player.PlayerLoginResp
import com.mikai233.common.message.world.PlayerLogin
import com.mikai233.world.WorldActor
import com.mikai233.world.data.PlayerAbstractMem
import io.github.mikai233.asteria.message.HandlerContext
import io.github.mikai233.asteria.message.MessageHandler

@AllOpen
@Suppress("unused")
class PlayerLoginHandler : MessageHandler<PlayerLogin> {
    override fun handle(context: HandlerContext, message: PlayerLogin) {
        val actor = context.requireActor<WorldActor>()
        val loginReq = message.req
        val channelActor = actor.sender
        val abstractMem = actor.manager.get<PlayerAbstractMem>()
        val playerAbstract = abstractMem.getByAccount(loginReq.account)
        if (playerAbstract == null) {
            val playerId = actor.nextId()
            actor.sessionManager.createOrUpdateSession(playerId, channelActor)
            actor.launch {
                val createReq = PlayerCreateReq(loginReq.account, playerId, actor.worldId, "", channelActor)
                actor.askPlayer<PlayerCreateResp>(createReq)
                val abstract = PlayerAbstract(playerId, actor.worldId, loginReq.account, "", 1, unixTimestamp())
                abstractMem.addAbstract(abstract)
            }
        } else {
            actor.sessionManager.createOrUpdateSession(playerAbstract.id, channelActor)
            actor.launch {
                actor.askPlayer<PlayerLoginResp>(
                    com.mikai233.common.message.player.PlayerLoginReq(
                        loginReq.account,
                        playerAbstract.id,
                        playerAbstract.worldId,
                        channelActor,
                    ),
                )
            }
        }
    }
}
