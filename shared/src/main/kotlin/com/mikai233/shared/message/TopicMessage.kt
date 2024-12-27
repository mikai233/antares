package com.mikai233.shared.message

import com.google.protobuf.GeneratedMessage
import com.mikai233.common.message.Message


sealed interface WorldTopicMessage : Message

data class ProtobufEnvelopeToWorldClient(val inner: GeneratedMessage) : WorldTopicMessage

sealed interface AllWorldTopicMessage : Message

data class ProtobufEnvelopeToAllWorldClient(val inner: GeneratedMessage) : AllWorldTopicMessage
