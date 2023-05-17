package com.mikai233.player.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.ext.logger
import com.mikai233.common.msg.MessageHandler
import com.mikai233.player.PlayerActor
import com.mikai233.protocol.ProtoLogin.LoginReq
import com.mikai233.shared.message.PlayerRunnable
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/17
 */
@AllOpen
class LoginHandler : MessageHandler {
    private val logger = logger()
    fun handleLoginReq(player: PlayerActor, req: LoginReq) {
        logger.info("{}", req)
        player.timerScheduler.startPeriodicTimer("hello", PlayerRunnable {
            logger.info("hello world")
        }, 200.milliseconds.toJavaDuration())
    }
}