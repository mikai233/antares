package com.mikai233.player.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.tell
import com.mikai233.common.msg.Handler
import com.mikai233.common.msg.MessageHandler
import com.mikai233.player.PlayerActor
import com.mikai233.player.service.loginService
import com.mikai233.protocol.testNotify
import com.mikai233.shared.message.PlayerInitDone
import com.mikai233.shared.message.WHPlayerCreate
import com.mikai233.shared.message.WHPlayerLogin
import kotlin.random.Random

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/17
 */
@AllOpen
class LoginHandler : MessageHandler {
    private val logger = logger()
    fun handleWHPlayerLogin(player: PlayerActor, playerLogin: WHPlayerLogin) {
        player.bindChannelActor(playerLogin.channelActor)
        //event
        val resp = loginService.loginSuccessResp(player)
        player.write(resp)
        repeat(100) {
            player.write(testNotify {
                data = Random.nextDouble().toString()
            })
        }
    }

    fun handleWHPlayerCreate(player: PlayerActor, playerCreate: WHPlayerCreate) {
        player.bindChannelActor(playerCreate.channelActor)
        //event
        loginService.createPlayer(player, playerCreate)
        player.context.self tell PlayerInitDone
        val resp = loginService.loginSuccessResp(player)
        player.write(resp)
    }
}

/**
 * TODO test only
 */
class WHPlayerLoginHandler : Handler<PlayerActor, WHPlayerLogin> {
    override fun handle(actor: PlayerActor, msg: WHPlayerLogin) {
        TODO("Not yet implemented")
    }
}
