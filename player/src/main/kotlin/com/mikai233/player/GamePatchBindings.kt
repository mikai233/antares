package com.mikai233.player

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.message.Message
import io.github.realmlabs.asteria.patch.PatchableServiceRegistry

data class GamePatchBindings(
    val services: PatchableServiceRegistry,
    val playerMessageRegistry: PlayerMessageHandlerRegistry<GeneratedMessage>,
    val playerInternalMessageRegistry: PlayerMessageHandlerRegistry<Message>,
)
