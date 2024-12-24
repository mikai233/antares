package com.mikai233.shared.message

import com.mikai233.common.message.Message

object HandoffShardActor : Message

data class ActorNamedRunnable(
    val name: String,
    val block: () -> Unit
) : Runnable, PlayerMessage, ChannelMessage, WorldMessage, ScriptProxyMessage {
    override fun run() {
        block()
    }
}

data class ExcelUpdate(val hashcode: Int) : BusinessPlayerMessage, BusinessWorldMessage

sealed interface ChannelMessage : Message

sealed interface SerdeChannelMessage : ChannelMessage

sealed interface PlayerMessage : Message

sealed interface SerdePlayerMessage : PlayerMessage

sealed interface BusinessPlayerMessage : SerdePlayerMessage

sealed interface WorldMessage : Message

sealed interface SerdeWorldMessage : WorldMessage

sealed interface BusinessWorldMessage : SerdeWorldMessage

sealed interface GlobalUidMessage : Message

sealed interface SerdeGlobalUidMessage : GlobalUidMessage

sealed interface BusinessSerdeGlobalUidMessage : SerdeGlobalUidMessage
