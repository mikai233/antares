package com.mikai233.common.entity

import com.mikai233.common.db.Entity
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "worker_id")
data class WorkerId(
    @Id
    val addr: String,
    val id: Int
) : Entity {
    companion object {
        @JvmStatic
        @PersistenceCreator
        fun create(): WorkerId {
            return WorkerId("", 0)
        }
    }
}
