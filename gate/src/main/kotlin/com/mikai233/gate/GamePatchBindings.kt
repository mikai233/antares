package com.mikai233.gate

import com.google.protobuf.GeneratedMessage
import io.github.realmlabs.asteria.patch.PatchableServiceRegistry

data class GamePatchBindings(
    val services: PatchableServiceRegistry,
    val gateMessageRegistry: ChannelMessageHandlerRegistry<GeneratedMessage>,
)
