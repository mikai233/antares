package com.mikai233.common.runtime

import com.mikai233.common.config.WORLD_RUNTIME_STATES
import com.mikai233.common.config.worldRuntimeStatePath
import io.github.realmlabs.asteria.config.center.ConfigRevisionMismatchException
import io.github.realmlabs.asteria.config.center.RuntimeConfigRepository
import org.apache.zookeeper.KeeperException

enum class WorldRuntimeStatus {
    Loading,
    Up,
    Stopping,
    Down,
}

data class WorldRuntimeState(
    val worldId: Long,
    val status: WorldRuntimeStatus,
    val nodeId: String,
    val nodeAddress: String,
    val updatedAtMillis: Long,
    val message: String? = null,
)

class WorldRuntimeStateStore(
    private val repository: RuntimeConfigRepository,
) {
    suspend fun put(state: WorldRuntimeState) {
        val path = worldRuntimeStatePath(state.worldId)
        while (true) {
            val current = repository.get<WorldRuntimeState>(path)
            if (current != null && current.value.updatedAtMillis > state.updatedAtMillis) {
                return
            }
            try {
                repository.put(path, state, current?.revision)
                return
            } catch (error: Exception) {
                if (!error.isRetryableWriteConflict()) {
                    throw error
                }
            }
        }
    }

    suspend fun list(): List<WorldRuntimeState> {
        return repository.children<WorldRuntimeState>(WORLD_RUNTIME_STATES)
            .values
            .values
            .map { it.value }
            .sortedBy { it.worldId }
    }
}

private fun Exception.isRetryableWriteConflict(): Boolean {
    return this is ConfigRevisionMismatchException ||
            this is KeeperException.NodeExistsException ||
            this is KeeperException.BadVersionException ||
            this is KeeperException.NoNodeException
}
