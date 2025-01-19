package com.mikai233.common.test.db

import com.mikai233.common.db.Entity
import org.springframework.data.annotation.Id

data class TestEntity(
    @Id val id: Int,
    var name: String,
    var age: Int,
) : Entity
