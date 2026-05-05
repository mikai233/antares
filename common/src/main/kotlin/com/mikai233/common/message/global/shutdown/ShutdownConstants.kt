package com.mikai233.common.message.global.shutdown

import com.mikai233.common.message.Message

const val GATE_DRAIN_TOPIC = "shutdown.gate-drain"

data object HandoffShutdownCoordinator : Message
