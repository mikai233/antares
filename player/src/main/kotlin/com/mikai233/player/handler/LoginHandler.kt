package com.mikai233.player.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.common.message.Handler
import com.mikai233.common.message.MessageHandler
import com.mikai233.player.PlayerActor
import com.mikai233.player.service.loginService
import com.mikai233.shared.message.player.PlayerInitialized
import com.mikai233.shared.message.player.WHPlayerCreate
import com.mikai233.shared.message.player.WHPlayerLogin
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
        val channelActor = player.sender
        player.bindChannelActor(channelActor)
        //event
        val resp = loginService.loginSuccessResp(player)
        player.send(resp)
        repeat(100) {
            player.send(testNotify {
                data = Random.nextDouble().toString()
            })
        }
    }

    fun handleWHPlayerCreate(player: PlayerActor, playerCreate: WHPlayerCreate) {
        val channelActor = player.sender
        player.bindChannelActor(channelActor)
        //event
        loginService.createPlayer(player, playerCreate)
        player.context.self tell PlayerInitialized
        val resp = loginService.loginSuccessResp(player)
        player.send(resp)
    }
}

/**
 * TODO test only
 */
class WHPlayerLoginHandler : Handler<PlayerActor, WHPlayerLogin> {
    override fun handle(actor: PlayerActor, msg: WHPlayerLogin) {
        actor.context.system.scheduler()
        TODO("Not yet implemented")
    }
}
