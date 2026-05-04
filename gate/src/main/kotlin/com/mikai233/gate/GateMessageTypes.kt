package com.mikai233.gate

import io.github.realmlabs.asteria.message.ActorHandlerContext
import io.github.realmlabs.asteria.message.MessageHandler
import io.github.realmlabs.asteria.message.PatchableMessageHandlerRegistry

typealias ChannelHandlerContext = ActorHandlerContext<ChannelActor>
typealias ChannelMessageHandler<M> = MessageHandler<ChannelHandlerContext, M>
typealias ChannelMessageHandlerRegistry<M> = PatchableMessageHandlerRegistry<ChannelHandlerContext, M>
