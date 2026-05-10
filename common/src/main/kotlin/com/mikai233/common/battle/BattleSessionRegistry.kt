package com.mikai233.common.battle

import java.util.concurrent.atomic.AtomicReference

interface BattleSessionRegistry {
    fun resolve(battleId: Long): BattleEndpoint?
}

class BattleEndpointRegistry(
    fallbackEndpoints: List<BattleEndpoint>,
) : BattleSessionRegistry {
    private val fallbackEndpoints: List<BattleEndpoint> = fallbackEndpoints.sortedBy { it.instanceId }
    private val discoveredEndpoints = AtomicReference<List<BattleEndpoint>>(emptyList())

    val endpoints: List<BattleEndpoint>
        get() = discoveredEndpoints.get().ifEmpty { fallbackEndpoints }

    override fun resolve(battleId: Long): BattleEndpoint? {
        require(battleId > 0) { "battleId must be positive" }
        val current = endpoints
        if (current.isEmpty()) {
            return null
        }
        return current[Math.floorMod(battleId, current.size)]
    }

    fun replaceDiscovered(endpoints: List<BattleEndpoint>) {
        discoveredEndpoints.set(endpoints.sortedBy { it.instanceId })
    }
}
