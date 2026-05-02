package com.mikai233.common.db.tracked

import com.mikai233.common.extension.logger
import io.github.mikai233.asteria.persistence.AutoFlushMemData
import io.github.mikai233.asteria.persistence.Entity
import org.springframework.data.mongodb.core.MongoTemplate

abstract class TrackedMemData<E, T>(
    private val slot: String,
    private val bucket: Int,
    mongoTemplate: () -> MongoTemplate,
    private val id: (E) -> Any?,
    private val factory: (TrackContext, E) -> T,
    private val fieldRoot: String = "",
) : AutoFlushMemData where E : Entity<*>, T : TrackedEntity<E> {
    private val logger = logger()
    protected val queue = PendingWriteQueue()
    private val flusher = MongoPendingWriteFlusher(queue, mongoTemplate)

    protected fun trackContext(entityId: Any?): TrackContext {
        return TrackContext(slot, bucket, entityId, queue, fieldRoot)
    }

    protected fun attachLoaded(entity: E): T {
        return factory(trackContext(id(entity)), entity)
    }

    protected fun createTracked(entity: E): T {
        return attachLoaded(entity).also { tracked ->
            enqueueCreated(tracked)
        }
    }

    private fun enqueueCreated(entity: T) {
        val persistentValue = persistentValueOf(entity)
        require(persistentValue is Map<*, *>) {
            "Tracked entity ${entity::class.qualifiedName} persistent value must be a map"
        }
        persistentValue.forEach { (fieldPath, value) ->
            val path = fieldPath?.toString() ?: return@forEach
            queue.enqueue(ChangeOp.Set(DbPath(slot, bucket, entity.trackId, rootedFieldPath(path)), value))
        }
    }

    private fun rootedFieldPath(fieldPath: String): String {
        return if (fieldRoot.isBlank()) {
            fieldPath
        } else {
            "${DbPath.encodePathPart(fieldRoot)}.$fieldPath"
        }
    }

    override suspend fun tick() {
        flush()
    }

    override suspend fun flush(): Boolean {
        return runCatching {
            flusher.flush()
        }.onFailure { throwable ->
            logger.error("flush tracked mem data failed, slot:{}", slot, throwable)
        }.isSuccess
    }
}
