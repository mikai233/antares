package com.mikai233.common.script

import com.mikai233.common.core.Node

internal interface NodeScriptFunction<T : Node> : Function1<T, Unit>
