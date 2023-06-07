package com.mikai233.shared.constants

enum class PlayerActionType(val id: Int) {
    Test(0),
    ;

    companion object {
        private val v = PlayerActionType.values().associateBy { it.id }
        operator fun get(id: Int) = requireNotNull(v[id]) { "playerActionType of id:$id not found" }
    }
}
