package com.mikai233.shared.message

import com.google.protobuf.GeneratedMessageV3


data class ChannelProtobufEnvelope(val inner: GeneratedMessageV3) : SerdeChannelMessage

data class ClientMessage(val inner: GeneratedMessageV3) : ChannelMessage

object StopChannel : ChannelMessage

data class ChannelExpired(val reason: Int) : SerdeChannelMessage

object ChannelAuthorized : ChannelMessage

data class ChannelWorldTopic(val inner: WorldTopicMessage) : ChannelMessage

data class ChannelAllWorldTopic(val inner: AllWorldTopicMessage) : ChannelMessage

object ChannelReceiveTimeout : ChannelMessage
