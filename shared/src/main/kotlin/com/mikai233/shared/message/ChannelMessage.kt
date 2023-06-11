package com.mikai233.shared.message

import com.google.protobuf.GeneratedMessageV3


data class ChannelProtobufEnvelope(val inner: GeneratedMessageV3) : SerdeChannelMessage

data class ClientMessage(val inner: GeneratedMessageV3) : ChannelMessage

data class StopChannel(val reason: StopReason) : ChannelMessage

enum class StopReason {
    ChannelInactive,
    UnexpectedMessage,
}

data class ChannelExpired(val reason: Int) : SerdeChannelMessage

object ChannelAuthorized : ChannelMessage

data class ChannelWorldTopic(val inner: WorldTopicMessage) : ChannelMessage

data class ChannelAllWorldTopic(val inner: AllWorldTopicMessage) : ChannelMessage
