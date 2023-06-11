package com.mikai233.world.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.entity.PlayerAbstract
import com.mikai233.common.ext.unixTimestamp
import com.mikai233.common.msg.MessageHandler
import com.mikai233.protocol.testNotify
import com.mikai233.shared.message.PlayerLogin
import com.mikai233.shared.message.WHPlayerCreate
import com.mikai233.shared.message.WHPlayerLogin
import com.mikai233.world.WorldActor
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.random.nextUInt

@AllOpen
class WorldLoginHandler : MessageHandler {
    fun handlePlayerLogin(world: WorldActor, playerLogin: PlayerLogin) {
        val loginReq = playerLogin.req
        val channelActor = playerLogin.channelActor
        val abstractMem = world.manager.playerAbstractMem
        val mayExistsAbstract = abstractMem.getByAccount(loginReq.account)
        if (mayExistsAbstract == null) {
            //new player TODO generate player id
            val playerId = Random.nextUInt().toLong()
            world.sessionManager.createOrUpdateSession(playerId, channelActor)
            val abstract = PlayerAbstract(playerId, world.worldId, loginReq.account, "", 1, unixTimestamp())
            abstractMem.createAbstract(abstract)
            world.tellPlayer(playerId, WHPlayerCreate(channelActor, loginReq.account, playerId, world.worldId, ""))
        } else {
            //exists player
            world.sessionManager.createOrUpdateSession(mayExistsAbstract.playerId, channelActor)
            world.tellPlayer(
                mayExistsAbstract.playerId,
                WHPlayerLogin(channelActor, loginReq.account, mayExistsAbstract.playerId, mayExistsAbstract.worldId)
            )
        }
        world.coroutine.launch {
            delay(3000)
            repeat(10) {
                world.sessionManager.broadcastWorldClient(testNotify { data = "test notify:$it" })
            }
        }
    }
}
