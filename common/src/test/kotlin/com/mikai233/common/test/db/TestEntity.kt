package com.mikai233.common.test.db

import com.mikai233.common.entity.FieldTraceableEntity
import org.springframework.data.annotation.Id

data class TestEntity(
    @Id val id: Int,
    var name: String,
    var age: Int
) : FieldTraceableEntity<Int> {
    override fun key(): Int {
        return id
    }
}