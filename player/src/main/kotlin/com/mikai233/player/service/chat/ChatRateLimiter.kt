package com.mikai233.player.service.chat

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

class ChatRateLimiter(
    private val windowMillis: Long,
    private val maxHitsPerWindow: Int,
) {
    private val states = ConcurrentHashMap<Long, PlayerChatRateState>()

    fun hit(playerId: Long, nowMillis: Long): Long {
        val state = states.computeIfAbsent(playerId) { PlayerChatRateState() }
        return state.hit(nowMillis, windowMillis, maxHitsPerWindow)
    }

    fun clear(playerId: Long) {
        states.remove(playerId)
    }

    private class PlayerChatRateState {
        private val timestamps = ArrayDeque<Long>()

        @Synchronized
        fun hit(nowMillis: Long, windowMillis: Long, maxHitsPerWindow: Int): Long {
            while (timestamps.isNotEmpty() && nowMillis - timestamps.peekFirst() > windowMillis) {
                timestamps.removeFirst()
            }
            if (timestamps.size >= maxHitsPerWindow) {
                return windowMillis - (nowMillis - timestamps.peekFirst())
            }
            timestamps.addLast(nowMillis)
            return 0
        }
    }
}
