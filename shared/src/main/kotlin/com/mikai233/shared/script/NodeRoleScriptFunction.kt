package com.mikai233.shared.script

import com.mikai233.common.core.Launcher
import com.mikai233.common.core.component.Role

interface NodeRoleScriptFunction<T : Launcher> : Function1<T, Unit> {
    val role: Role
}
