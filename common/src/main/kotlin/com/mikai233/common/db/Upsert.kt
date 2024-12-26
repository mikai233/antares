package com.mikai233.common.db

import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.UpdateDefinition

data class Upsert(
    val query: Query,
    val update: UpdateDefinition,
    val record: Record,
)
