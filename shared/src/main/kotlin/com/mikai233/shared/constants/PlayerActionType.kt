package com.mikai233.shared.constants

enum class PlayerActionType(val id: Int) {
    Test(0),
    ExcelVersion(1),
    ;

    companion object {
        private val v = entries.associateBy { it.id }
        operator fun get(id: Int) = requireNotNull(v[id]) { "playerActionType of id:$id not found" }
    }
}
