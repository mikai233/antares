package com.mikai233.shared.message

import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.msg.SerdeMessage

sealed interface WorldTopicMessage : SerdeMessage

data class ProtobufEnvelopeToWorldClient(val inner: GeneratedMessageV3) : WorldTopicMessage

sealed interface AllWorldTopicMessage : SerdeMessage

data class ProtobufEnvelopeToAllWorldClient(val inner: GeneratedMessageV3) : AllWorldTopicMessage
