package com.mikai233.player

import io.github.realmlabs.asteria.message.ActorHandlerContext
import io.github.realmlabs.asteria.message.MessageHandler
import io.github.realmlabs.asteria.message.PatchableMessageHandlerRegistry

typealias PlayerHandlerContext = ActorHandlerContext<PlayerActor>
typealias PlayerMessageHandler<M> = MessageHandler<PlayerHandlerContext, M>
typealias PlayerMessageHandlerRegistry<M> = PatchableMessageHandlerRegistry<PlayerHandlerContext, M>
