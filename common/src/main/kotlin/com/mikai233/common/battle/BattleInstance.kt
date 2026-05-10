package com.mikai233.common.battle

data class BattleInstance(
    val instanceId: String,
    val host: String,
    val port: Int,
    val transport: String = BattleEndpoint.DEFAULT_TRANSPORT,
    val load: Int = 0,
    val state: BattleInstanceState = BattleInstanceState.UP,
) {
    fun toEndpoint(): BattleEndpoint {
        return BattleEndpoint(
            instanceId = instanceId,
            host = host,
            port = port,
            transport = transport,
        )
    }
}

enum class BattleInstanceState {
    UP,
    DRAINING,
    DOWN,
}
