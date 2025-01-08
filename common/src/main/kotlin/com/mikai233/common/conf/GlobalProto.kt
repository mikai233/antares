package com.mikai233.common.conf

import com.google.protobuf.GeneratedMessage
import com.google.protobuf.Parser
import kotlin.reflect.KClass

private typealias Message = KClass<out GeneratedMessage>
private typealias MessageParser = Parser<out GeneratedMessage>
private typealias MessageMap = MutableMap<Message, Int>
private typealias ParserMap = MutableMap<Int, MessageParser>

