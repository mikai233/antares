package com.mikai233.shared.message

import com.mikai233.common.msg.Message

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

sealed interface SerdeChannelMessage : ChannelMessage, SerdeMessage

sealed interface PlayerMessage : Message

sealed interface SerdePlayerMessage : PlayerMessage, SerdeMessage

sealed interface BusinessPlayerMessage : SerdePlayerMessage

sealed interface WorldMessage : Message

sealed interface SerdeWorldMessage : WorldMessage, SerdeMessage

sealed interface BusinessWorldMessage : SerdeWorldMessage

sealed interface ScriptMessage : Message

sealed interface SerdeScriptMessage : ScriptMessage, SerdeMessage

sealed interface GlobalUidMessage : Message

sealed interface SerdeGlobalUidMessage : GlobalUidMessage, SerdeMessage

sealed interface BusinessSerdeGlobalUidMessage : SerdeGlobalUidMessage
