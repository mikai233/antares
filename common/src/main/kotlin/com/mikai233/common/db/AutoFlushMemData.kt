package com.mikai233.common.db

interface AutoFlushMemData {
    fun tick()

    fun flush(): Boolean
}
