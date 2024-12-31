package com.mikai233.player.service

import com.mikai233.common.annotation.AllOpen
import com.mikai233.player.PlayerActor
import com.mikai233.player.data.PlayerMem
import com.mikai233.protocol.ProtoLogin
import com.mikai233.protocol.ProtoLogin.LoginResp
import com.mikai233.protocol.loginResp
import com.mikai233.protocol.playerData
import com.mikai233.shared.entity.Player
import com.mikai233.shared.message.player.PlayerCreateReq

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/17
 */
@AllOpen
class LoginService {
    fun createPlayer(player: PlayerActor, playerCreateReq: PlayerCreateReq) {
        val entity = Player(
            id = playerCreateReq.playerId,
            account = playerCreateReq.account,
            worldId = playerCreateReq.worldId,
            nickname = playerCreateReq.nickname,
            level = 1
        )
        player.manager.get<PlayerMem>().initPlayer(entity)
    }

    fun loginSuccessResp(player: PlayerActor): LoginResp {
        return loginResp {
            result = ProtoLogin.LoginResult.Success
            data = playerData {
                playerId = player.playerId
                nickname = player.manager.get<PlayerMem>().player.nickname
            }
        }
    }
}
