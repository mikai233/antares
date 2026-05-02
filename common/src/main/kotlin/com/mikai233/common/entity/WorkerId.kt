package com.mikai233.common.entity

import com.mikai233.common.db.tracked.TrackEntity
import io.github.mikai233.asteria.persistence.Entity
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@TrackEntity
@Document(collection = "worker_id")
data class WorkerId(
    @Id
    override val id: String,
    val workerId: Int,
) : Entity<String> {
    companion object {
        @JvmStatic
        @PersistenceCreator
        fun create(): WorkerId {
            return WorkerId("", 0)
        }
    }
}
