package com.mikai233.common.config

import com.mikai233.common.db.AsteriaTrackedMemData
import com.mikai233.common.db.MongoDB
import com.mikai233.common.entity.ActorConfigSyncState
import com.mikai233.common.entity.ActorConfigSyncStateMongo
import com.mikai233.common.entity.ActorConfigSyncStateTracked
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query

class ActorConfigSyncMem(
    private val actorKind: String,
    private val actorEntityId: String,
    private val mongoDbProvider: () -> MongoDB,
) : AsteriaTrackedMemData<ActorConfigSyncState, ActorConfigSyncStateTracked>(
    ActorConfigSyncStateMongo.COLLECTION,
    { mongoDbProvider().database },
    ActorConfigSyncStateMongo::wrap,
) {
    private var state: ActorConfigSyncStateTracked? = null

    override suspend fun load() {
        val loaded: ActorConfigSyncState? = mongoDbProvider().reactiveTemplate
            .findOne(query(where("_id").`is`(documentId())), ActorConfigSyncState::class.java, ActorConfigSyncStateMongo.COLLECTION)
            .awaitSingleOrNull()
        if (loaded != null) {
            state = attachLoaded(loaded)
        }
    }

    fun currentRevision(): String? {
        val revision = state?.revision.orEmpty()
        return revision.ifBlank { null }
    }

    fun updateRevision(revision: String, updatedAt: Long = System.currentTimeMillis()) {
        val tracked = state ?: createTracked(
            ActorConfigSyncState(
                id = documentId(),
                actorKind = actorKind,
                actorEntityId = actorEntityId,
                revision = revision,
                updatedAt = updatedAt,
            ),
        ).also { state = it }
        tracked.revision = revision
        tracked.updatedAt = updatedAt
    }

    private fun documentId(): String = "${actorKind}_${actorEntityId}"
}
