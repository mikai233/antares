package com.mikai233.common.test.db

import io.github.mikai233.asteria.persistence.Entity
import org.springframework.data.annotation.Id

data class TestEntity(
    @Id override val id: Int,
    var name: String,
    var age: Int,
) : Entity<Int>
