package com.mikai233.shared.script

import com.mikai233.common.core.Launcher

internal interface NodeScriptFunction<T : Launcher> : Function1<T, Unit>