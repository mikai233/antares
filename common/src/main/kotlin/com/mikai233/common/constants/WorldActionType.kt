package com.mikai233.common.constants

enum class WorldActionType(val id: Int) {
    Test(0),
    GameConfigVersion(1),
    ;

    companion object {
        private val v = entries.associateBy { it.id }
        operator fun get(id: Int) = requireNotNull(v[id]) { "worldActionType of id:$id not found" }
    }
}
