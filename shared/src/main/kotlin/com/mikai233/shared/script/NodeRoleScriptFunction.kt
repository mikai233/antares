package com.mikai233.shared.script

import com.mikai233.common.core.components.Role

interface NodeRoleScriptFunction : NodeScriptFunction {
    val role: Role
}