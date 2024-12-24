package com.mikai233.common.script

import com.mikai233.common.core.Node

interface NodeRoleScriptFunction<T : Node> : Function1<T, Unit>
