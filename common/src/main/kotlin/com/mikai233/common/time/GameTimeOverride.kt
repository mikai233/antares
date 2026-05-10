package com.mikai233.common.time

import kotlinx.coroutines.flow.Flow

data class GameTimeOverride(
    val epoch: Long,
    val globalOffsetMillis: Long,
) {
    init {
        require(epoch >= 0) { "game time override epoch must not be negative" }
    }
}

data class GameTimeReloadAck(
    val epoch: Long,
    val nodeId: String,
    val role: String,
    val success: Boolean,
    val error: String? = null,
) {
    init {
        require(epoch >= 0) { "game time reload ack epoch must not be negative" }
        require(nodeId.isNotBlank()) { "game time reload ack nodeId must not be blank" }
        require(role.isNotBlank()) { "game time reload ack role must not be blank" }
    }
}

interface GameTimeOverrideStore {
    suspend fun current(): GameTimeOverride

    fun watch(): Flow<GameTimeOverride>

    suspend fun updateGlobalOffset(globalOffsetMillis: Long): GameTimeOverride

    suspend fun ack(ack: GameTimeReloadAck)

    suspend fun acks(epoch: Long): List<GameTimeReloadAck>
}
