package com.mikai233.player.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.tell
import com.mikai233.common.message.Handle
import com.mikai233.common.message.Handler
import com.mikai233.common.message.MessageHandler
import com.mikai233.player.PlayerActor
import com.mikai233.player.service.loginService
import com.mikai233.protocol.testNotify
import com.mikai233.shared.message.player.PlayerCreateReq
import com.mikai233.shared.message.player.PlayerCreateResp
import com.mikai233.shared.message.player.PlayerLoginReq
import com.mikai233.shared.message.player.PlayerLoginResp
import kotlin.random.Random

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/17
 */
@AllOpen
class LoginHandler : MessageHandler {
    @Handle
    fun handlePlayerLoginReq(player: PlayerActor, playerLoginReq: PlayerLoginReq) {
        player.bindChannelActor(playerLoginReq.channelActor)
        //event
        val resp = loginService.loginSuccessResp(player)
        player.send(resp)
        player.sender.tell(PlayerLoginResp)
        repeat(100) {
            player.send(testNotify {
                data = Random.nextDouble().toString()
            })
        }
    }

    @Handle
    fun handlePlayerCreateReq(player: PlayerActor, playerCreateReq: PlayerCreateReq) {
        player.bindChannelActor(playerCreateReq.channelActor)
        //event
        loginService.createPlayer(player, playerCreateReq)
        player.sender.tell(PlayerCreateResp)
        val resp = loginService.loginSuccessResp(player)
        player.send(resp)
    }
}

/**
 * TODO test only
 */
class WHPlayerLoginHandler : Handler<PlayerActor, PlayerLoginReq> {
    override fun handle(actor: PlayerActor, msg: PlayerLoginReq) {
        actor.context.system.scheduler()
        TODO("Not yet implemented")
    }
}
