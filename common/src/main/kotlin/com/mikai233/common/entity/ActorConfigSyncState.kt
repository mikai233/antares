package com.mikai233.common.entity

import io.github.realmlabs.asteria.persistence.Entity
import io.github.realmlabs.asteria.persistence.mongodb.annotations.AsteriaMongoEntity
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "actor_config_sync_state")
@AsteriaMongoEntity(
    collection = "actor_config_sync_state",
    wrapperName = "ActorConfigSyncStateTracked",
    helperName = "ActorConfigSyncStateMongo",
)
data class ActorConfigSyncState(
    override val id: String,
    val actorKind: String,
    val actorEntityId: String,
    var revision: String,
    var updatedAt: Long,
) : Entity<String> {
    companion object {
        @JvmStatic
        fun defaults(): ActorConfigSyncState {
            return ActorConfigSyncState("", "", "", "", 0L)
        }

        @JvmStatic
        @PersistenceCreator
        fun create(
            id: String?,
            actorKind: String?,
            actorEntityId: String?,
            revision: String?,
            updatedAt: Long?,
        ): ActorConfigSyncState {
            val defaults = defaults()
            return ActorConfigSyncState(
                id = id ?: defaults.id,
                actorKind = actorKind ?: defaults.actorKind,
                actorEntityId = actorEntityId ?: defaults.actorEntityId,
                revision = revision ?: defaults.revision,
                updatedAt = updatedAt ?: defaults.updatedAt,
            )
        }
    }
}
