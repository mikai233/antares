package com.mikai233.common.core.components

import com.mikai233.common.core.Server

interface Component {
    fun init(server: Server)

    fun shutdown()
}