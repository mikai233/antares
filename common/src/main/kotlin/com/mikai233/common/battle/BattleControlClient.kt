package com.mikai233.common.battle

import com.mikai233.protocol.ProtoBattle.BattleStartResp
import com.mikai233.protocol.ProtoBattle.BattleStartResult
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

interface BattleControlClient {
    fun startBattle(
        playerId: Long,
        mode: String,
        clientSeq: Long,
    ): BattleStartResp
}

class DirectBattleControlClient(
    private val registry: BattleSessionRegistry,
    private val tokenCodec: BattleTokenCodec,
    private val battleIdGenerator: () -> Long,
    private val tokenTtl: Duration = 5.minutes,
) : BattleControlClient {
    override fun startBattle(
        playerId: Long,
        mode: String,
        clientSeq: Long,
    ): BattleStartResp {
        val battleId = battleIdGenerator()
        val endpoint = registry.resolve(battleId) ?: return unavailable(clientSeq, "battle endpoint not configured")
        val expiresAt = Clock.System.now().plus(tokenTtl).toEpochMilliseconds()
        return BattleStartResp.newBuilder()
            .setClientSeq(clientSeq)
            .setResult(BattleStartResult.BattleStartSuccess)
            .setBattleId(battleId)
            .setEndpoint(endpoint.toProto())
            .setToken(tokenCodec.issue(battleId, playerId, expiresAt))
            .setTokenExpiresAt(expiresAt)
            .build()
    }

    private fun unavailable(clientSeq: Long, reason: String): BattleStartResp {
        return BattleStartResp.newBuilder()
            .setClientSeq(clientSeq)
            .setResult(BattleStartResult.BattleStartUnavailable)
            .setReason(reason)
            .build()
    }
}

private fun BattleEndpoint.toProto(): com.mikai233.protocol.ProtoBattle.BattleEndpoint {
    return com.mikai233.protocol.ProtoBattle.BattleEndpoint.newBuilder()
        .setInstanceId(instanceId)
        .setHost(host)
        .setPort(port)
        .setTransport(transport)
        .build()
}
