package com.mikai233.common.test.db

import io.github.realmlabs.asteria.persistence.Entity
import org.springframework.data.annotation.Id

class TraceDBTest {
    data class ChildData(val field1: String, var field2: Long)
    data class RootEntity(
        @Id
        override val id: Long,
        val field1: String,
        val field2: HashMap<Int, Int>,
        var field3: HashMap<Int, Int>,
        val field4: MutableList<String>,
        var field5: HashMap<Int, String?>,
        var field6: ChildData?,
    ) : Entity<Long>

    data class TraceFieldEntity(
        val id: Long,
        var field1: Int,
        var field2: MutableList<Int>,
        val field3: MutableMap<Int, Int?>,
        var field4: MutableSet<Int>?,
        var field5: ChildData?,
    )
}
