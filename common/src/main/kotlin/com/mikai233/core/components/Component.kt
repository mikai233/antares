package com.mikai233.core.components

import com.mikai233.core.Server

interface Component {
    fun init(server: Server)

    fun shutdown(server: Server)
}