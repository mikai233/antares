package com.mikai233.world

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.message.Message
import io.github.realmlabs.asteria.patch.PatchableServiceRegistry

data class GamePatchBindings(
    val services: PatchableServiceRegistry,
    val worldMessageRegistry: WorldMessageHandlerRegistry<GeneratedMessage>,
    val worldInternalMessageRegistry: WorldMessageHandlerRegistry<Message>,
)
