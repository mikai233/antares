package com.mikai233.player.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.event.PlayerLoginEvent
import com.mikai233.common.extension.tell
import com.mikai233.common.message.Handle
import com.mikai233.common.message.MessageHandler
import com.mikai233.common.message.player.PlayerCreateReq
import com.mikai233.common.message.player.PlayerCreateResp
import com.mikai233.common.message.player.PlayerLoginReq
import com.mikai233.common.message.player.PlayerLoginResp
import com.mikai233.player.PlayerActor
import com.mikai233.player.service.loginService
import com.mikai233.protocol.testNotify
import kotlin.random.Random

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/17
 */
@AllOpen
@Suppress("unused")
class LoginHandler : MessageHandler {
    @Handle
    fun handlePlayerLoginReq(player: PlayerActor, playerLoginReq: PlayerLoginReq) {
        player.bindChannelActor(playerLoginReq.channelActor)
        val resp = loginService.loginSuccessResp(player)
        player.send(resp)
        player.sender.tell(PlayerLoginResp)
        player.fireEvent(PlayerLoginEvent)
        repeat(100) {
            player.send(testNotify {
                data = Random.nextDouble().toString()
            })
        }
    }

    @Handle
    fun handlePlayerCreateReq(player: PlayerActor, req: PlayerCreateReq) {
        player.bindChannelActor(req.channelActor)
        //event
        loginService.createPlayer(player, req)
        player.sender.tell(PlayerCreateResp)
        val resp = loginService.loginSuccessResp(player)
        player.send(resp)
    }
}
