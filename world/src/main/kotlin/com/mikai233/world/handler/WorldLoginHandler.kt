package com.mikai233.world.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.extension.unixTimestamp
import com.mikai233.common.message.Handle
import com.mikai233.common.message.MessageHandler
import com.mikai233.shared.entity.PlayerAbstract
import com.mikai233.shared.message.player.PlayerCreateReq
import com.mikai233.shared.message.player.PlayerCreateResp
import com.mikai233.shared.message.player.PlayerLoginResp
import com.mikai233.shared.message.world.PlayerLogin
import com.mikai233.world.WorldActor
import com.mikai233.world.data.PlayerAbstractMem
import kotlin.random.Random
import kotlin.random.nextUInt

@AllOpen
class WorldLoginHandler : MessageHandler {
    @Handle
    fun handlePlayerLogin(world: WorldActor, playerLogin: PlayerLogin) {
        val loginReq = playerLogin.req
        val channelActor = world.sender
        val abstractMem = world.manager.get<PlayerAbstractMem>()
        val playerAbstract = abstractMem.getByAccount(loginReq.account)
        if (playerAbstract == null) {
            //new player TODO generate player id
            val playerId = Random.nextUInt().toLong()
            world.sessionManager.createOrUpdateSession(playerId, channelActor)
            world.launch {
                val createReq = PlayerCreateReq(loginReq.account, playerId, world.worldId, "", channelActor)
                world.askPlayer<PlayerCreateResp>(createReq)
                val abstract = PlayerAbstract(playerId, world.worldId, loginReq.account, "", 1, unixTimestamp())
                abstractMem.addAbstract(abstract)
            }
        } else {
            //exists player
            world.sessionManager.createOrUpdateSession(playerAbstract.playerId, channelActor)
            world.launch {
                world.askPlayer<PlayerLoginResp>(
                    com.mikai233.shared.message.player.PlayerLoginReq(
                        loginReq.account,
                        playerAbstract.playerId,
                        playerAbstract.worldId,
                        channelActor,
                    )
                )
            }
        }
    }
}
