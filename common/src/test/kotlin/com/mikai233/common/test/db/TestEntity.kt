package com.mikai233.common.test.db

import org.springframework.data.annotation.Id

data class TestEntity(
    @Id val id: Int,
    var name: String,
    var age: Int
) : TraceableFieldEntity<Int> {
    override fun id(): Int {
        return id
    }
}
