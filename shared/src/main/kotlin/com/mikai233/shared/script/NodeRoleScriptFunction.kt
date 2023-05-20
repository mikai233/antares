package com.mikai233.shared.script

import com.mikai233.common.core.Launcher
import com.mikai233.common.core.components.Role

interface NodeRoleScriptFunction<T : Launcher> : Function1<T, Unit> {
    val role: Role
}