package com.mikai233.common.message

import com.google.protobuf.GeneratedMessage

sealed interface ChannelMessage

/**
 * @param id protobuf message id
 */
data class ClientProtobuf(val id: Int, val message: GeneratedMessage) : ChannelMessage

data object StopChannel : ChannelMessage

data class ChannelExpired(val reason: Int) : ChannelMessage

data object ChannelAuthorized : ChannelMessage
