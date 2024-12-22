package com.mikai233.shared.message

import com.google.protobuf.GeneratedMessage


data class ChannelProtobufEnvelope(val message: GeneratedMessage) : SerdeChannelMessage

data class ClientMessage(val id: Int, val message: GeneratedMessage) : ChannelMessage

data object StopChannel : ChannelMessage

data class ChannelExpired(val reason: Int) : SerdeChannelMessage

data object ChannelAuthorized : ChannelMessage

data class ChannelWorldTopic(val inner: WorldTopicMessage) : ChannelMessage

data class ChannelAllWorldTopic(val inner: AllWorldTopicMessage) : ChannelMessage

data object ChannelReceiveTimeout : ChannelMessage
