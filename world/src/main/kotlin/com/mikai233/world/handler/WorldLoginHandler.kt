package com.mikai233.world.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.entity.PlayerAbstract
import com.mikai233.common.extension.unixTimestamp
import com.mikai233.common.message.MessageHandler
import com.mikai233.protocol.testNotify
import com.mikai233.shared.message.player.PlayerCreate
import com.mikai233.shared.message.world.PlayerLogin
import com.mikai233.world.WorldActor
import com.mikai233.world.data.PlayerAbstractMem
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.random.nextUInt

@AllOpen
class WorldLoginHandler : MessageHandler {
    fun handlePlayerLogin(world: WorldActor, playerLogin: PlayerLogin) {
        val loginReq = playerLogin.req
        val channelActor = world.sender
        val abstractMem = world.manager.get<PlayerAbstractMem>()
        val playerAbstract = abstractMem.getByAccount(loginReq.account)
        if (playerAbstract == null) {
            //new player TODO generate player id
            val playerId = Random.nextUInt().toLong()
            world.sessionManager.createOrUpdateSession(playerId, channelActor)
            val abstract = PlayerAbstract(playerId, world.worldId, loginReq.account, "", 1, unixTimestamp())
            abstractMem.addAbstract(abstract)
            world.forwardPlayer(PlayerCreate(loginReq.account, playerId, world.worldId, ""))
        } else {
            //exists player
            world.sessionManager.createOrUpdateSession(playerAbstract.playerId, channelActor)
            world.forwardPlayer(
                com.mikai233.shared.message.player.PlayerLogin(
                    loginReq.account,
                    playerAbstract.playerId,
                    playerAbstract.worldId
                )
            )
        }
        world.launch {
            delay(3000)
            repeat(10) {
                world.sessionManager.broadcastWorldClient(testNotify { data = "test notify:$it" })
            }
        }
    }
}
