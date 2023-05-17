package com.mikai233.shared.message

import com.mikai233.common.msg.Message
import com.mikai233.common.msg.SerdeMessage

sealed interface ChannelMessage : Message

sealed interface SerdeChannelMessage : ChannelMessage, SerdeMessage

sealed interface PlayerMessage : Message

sealed interface SerdePlayerMessage : PlayerMessage, SerdeMessage

sealed interface WorldMessage : Message

sealed interface SerdeWorldMessage : WorldMessage, SerdeMessage