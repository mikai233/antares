package com.mikai233.common.runtime

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.pattern.Patterns
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class LocalEntityRegistry {
    private val entities = ConcurrentHashMap<String, RegisteredLocalEntity>()

    fun register(kind: String, entityId: String, ref: ActorRef) {
        entities[key(kind, entityId)] = RegisteredLocalEntity(kind, entityId, ref)
    }

    fun unregister(kind: String, entityId: String, ref: ActorRef) {
        entities.remove(key(kind, entityId), RegisteredLocalEntity(kind, entityId, ref))
    }

    fun snapshot(kind: String): List<RegisteredLocalEntity> {
        return entities.values
            .filter { it.kind == kind }
            .sortedBy { it.entityId }
    }

    suspend fun stop(
        kind: String,
        stopMessage: Any,
        timeout: Duration,
        maxConcurrency: Int = DEFAULT_STOP_CONCURRENCY,
    ): LocalEntityStopResult {
        require(maxConcurrency > 0) { "local entity stop concurrency must be greater than zero" }
        val targets = snapshot(kind)
        val semaphore = Semaphore(maxConcurrency)
        val results = coroutineScope {
            targets.map { target ->
                async {
                    semaphore.withPermit {
                        val result = runCatching {
                            Patterns.gracefulStop(target.ref, timeout.toJavaDuration(), stopMessage).await()
                        }
                        LocalEntityStopResult.Entry(
                            entityId = target.entityId,
                            success = result.getOrDefault(false),
                            error = result.exceptionOrNull()?.message,
                        )
                    }
                }
            }.awaitAll()
        }
        return LocalEntityStopResult(kind, results)
    }

    private fun key(kind: String, entityId: String): String = "$kind:$entityId"

    private companion object {
        const val DEFAULT_STOP_CONCURRENCY = 128
    }
}

data class RegisteredLocalEntity(
    val kind: String,
    val entityId: String,
    val ref: ActorRef,
)

data class LocalEntityStopResult(
    val kind: String,
    val entries: List<Entry>,
) {
    val stopped: Int get() = entries.count { it.success }

    val failed: Int get() = entries.count { !it.success }

    data class Entry(
        val entityId: String,
        val success: Boolean,
        val error: String?,
    )
}
