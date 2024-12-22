package com.mikai233.shared.message

import com.google.protobuf.GeneratedMessageV3

sealed interface WorldTopicMessage : SerdeMessage

data class ProtobufEnvelopeToWorldClient(val inner: GeneratedMessageV3) : WorldTopicMessage

sealed interface AllWorldTopicMessage : SerdeMessage

data class ProtobufEnvelopeToAllWorldClient(val inner: GeneratedMessageV3) : AllWorldTopicMessage
