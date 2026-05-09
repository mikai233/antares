package com.mikai233.gate.message

import com.google.protobuf.GeneratedMessage

/**
 * @param id protobuf message id
 */
data class ClientProtobuf(val id: Int, val message: GeneratedMessage) : ChannelMessage
