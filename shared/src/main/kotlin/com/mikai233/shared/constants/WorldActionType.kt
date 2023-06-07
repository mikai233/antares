package com.mikai233.shared.constants

enum class WorldActionType(val id: Int) {
    Test(0),
    ;

    companion object {
        private val v = values().associateBy { it.id }
        operator fun get(id: Int) = requireNotNull(v[id]) { "worldActionType of id:$id not found" }
    }
}
