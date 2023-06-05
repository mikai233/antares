package com.mikai233.world.handler

import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.ext.shardingEnvelope
import com.mikai233.common.msg.MessageHandler
import com.mikai233.protocol.ProtoLogin
import com.mikai233.protocol.loginResp
import com.mikai233.protocol.playerData
import com.mikai233.shared.message.PlayerLogin
import com.mikai233.shared.message.WHPlayerLogin
import com.mikai233.world.WorldActor
import kotlin.random.Random
import kotlin.random.nextUInt

@AllOpen
class WorldLoginHandler : MessageHandler {
    fun handlePlayerLogin(world: WorldActor, playerLogin: PlayerLogin) {
        val loginReq = playerLogin.req
        val channelActor = playerLogin.channelActor
        //generate playerId
        val session = world.sessionManager.createOrUpdateSession(Random.nextUInt().toLong(), channelActor)
        val playerId = Random.nextUInt().toLong()
        session.writeProtobuf(loginResp {
            playerData = playerData {
                this.playerId = playerId
                nickname = "mikai233"
            }
            result = ProtoLogin.LoginResult.Success
        })
        world.playerActor.tell(shardingEnvelope("$playerId", WHPlayerLogin("mikai233", playerId, world.worldId)))
    }
}
