package com.mikai233.shared.message

import com.mikai233.common.core.components.Role
import com.mikai233.shared.script.ScriptType

data class NodeRoleScript(val role: Role, val type: ScriptType, val body: ByteArray) : SerdeScriptMessage {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NodeRoleScript

        if (role != other.role) return false
        if (type != other.type) return false
        return body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = role.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }
}

data class NodeScript(val type: ScriptType, val body: ByteArray) : SerdeScriptMessage {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NodeScript

        if (type != other.type) return false
        return body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }
}

