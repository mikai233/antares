package com.mikai233.common.time

import com.mikai233.common.config.GAME_TIME_OVERRIDE
import com.mikai233.common.config.gameTimeReloadAckPath
import io.github.realmlabs.asteria.config.center.ConfigRevisionMismatchException
import io.github.realmlabs.asteria.config.center.RuntimeConfigEvent
import io.github.realmlabs.asteria.config.center.RuntimeConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import org.apache.zookeeper.KeeperException

class ConfigCenterGameTimeOverrideStore(
    private val repository: RuntimeConfigRepository,
) : GameTimeOverrideStore {
    override suspend fun current(): GameTimeOverride {
        return repository.get<GameTimeOverride>(GAME_TIME_OVERRIDE)?.value ?: DEFAULT
    }

    override fun watch(): Flow<GameTimeOverride> {
        return repository.watchValue<GameTimeOverride>(GAME_TIME_OVERRIDE)
            .mapNotNull { event ->
                when (event) {
                    is RuntimeConfigEvent.Upserted -> event.value.value
                    is RuntimeConfigEvent.Deleted -> DEFAULT
                }
            }
    }

    override suspend fun updateGlobalOffset(globalOffsetMillis: Long): GameTimeOverride {
        while (true) {
            val current = repository.get<GameTimeOverride>(GAME_TIME_OVERRIDE)
            val next = GameTimeOverride(
                epoch = (current?.value?.epoch ?: DEFAULT.epoch) + 1,
                globalOffsetMillis = globalOffsetMillis,
            )
            try {
                repository.put(
                    path = GAME_TIME_OVERRIDE,
                    value = next,
                    expectedRevision = current?.revision,
                )
                return next
            } catch (error: Exception) {
                if (!error.isRetryableWriteConflict()) {
                    throw error
                }
                continue
            }
        }
    }

    suspend fun ensureInitial(globalOffsetMillis: Long): GameTimeOverride {
        val current = repository.get<GameTimeOverride>(GAME_TIME_OVERRIDE)
        if (current != null) {
            return current.value
        }
        val initial = GameTimeOverride(epoch = 0, globalOffsetMillis = globalOffsetMillis)
        return try {
            repository.put(GAME_TIME_OVERRIDE, initial)
            initial
        } catch (_: ConfigRevisionMismatchException) {
            current()
        } catch (_: KeeperException.NodeExistsException) {
            current()
        }
    }

    override suspend fun ack(ack: GameTimeReloadAck) {
        repository.put(gameTimeReloadAckPath(ack.epoch, ack.nodeId), ack)
    }

    override suspend fun acks(epoch: Long): List<GameTimeReloadAck> {
        require(epoch >= 0) { "game time reload ack epoch must not be negative" }
        return repository.children<GameTimeReloadAck>(gameTimeReloadAckPath(epoch))
            .values
            .values
            .map { it.value }
            .sortedBy { it.nodeId }
    }

    private companion object {
        val DEFAULT = GameTimeOverride(epoch = 0, globalOffsetMillis = 0)
    }
}

private fun Exception.isRetryableWriteConflict(): Boolean {
    return this is ConfigRevisionMismatchException || this is KeeperException.NodeExistsException
}
