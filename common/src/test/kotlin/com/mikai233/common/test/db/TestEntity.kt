package com.mikai233.common.test.db

import io.github.realmlabs.asteria.persistence.Entity

data class TestEntity(
    override val id: Int,
    var name: String,
    var age: Int,
) : Entity<Int>
