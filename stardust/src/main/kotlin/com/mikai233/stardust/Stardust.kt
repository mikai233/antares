package com.mikai233.stardust

import com.mikai233.GateNode
import com.mikai233.player.PlayerNode

fun main() {
    GateNode(sameJvm = true).launch()
    PlayerNode(sameJvm = true).launch()
}