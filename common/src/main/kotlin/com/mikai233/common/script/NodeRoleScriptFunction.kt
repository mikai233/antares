package com.mikai233.common.script

import com.mikai233.common.core.Node

interface NodeRoleScriptFunction<T : Node> : Function2<T, ByteArray?, Unit>
