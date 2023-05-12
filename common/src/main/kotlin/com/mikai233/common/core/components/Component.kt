package com.mikai233.common.core.components

interface Component {
    fun init(server: com.mikai233.common.core.Server)

    fun shutdown()
}