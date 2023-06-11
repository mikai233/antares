package com.mikai233.client

import com.mikai233.common.conf.GlobalEnv

fun main() {
    GameClient("localhost", GlobalEnv.loginPort).start()
}
