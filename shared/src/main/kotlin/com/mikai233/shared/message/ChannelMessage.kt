package com.mikai233.shared.message

import com.google.protobuf.GeneratedMessage


data class ServerProtobuf(val message: GeneratedMessage) : ChannelMessage

/**
 * @param id protobuf message id
 */
data class ClientProtobuf(val id: Int, val message: GeneratedMessage) : ChannelMessage

data object StopChannel : ChannelMessage

data class ChannelExpired(val reason: Int) : ChannelMessage

data object ChannelAuthorized : ChannelMessage

data class ChannelWorldTopic(val inner: WorldTopicMessage) : ChannelMessage

data class ChannelAllWorldTopic(val inner: AllWorldTopicMessage) : ChannelMessage
