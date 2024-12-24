package com.mikai233.common.conf

import com.google.protobuf.Descriptors
import com.google.protobuf.GeneratedMessage
import com.google.protobuf.Parser
import com.mikai233.common.extension.logger
import org.reflections.Reflections
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredFunctions

private typealias Message = KClass<out GeneratedMessage>
private typealias MessageParser = Parser<out GeneratedMessage>
private typealias MessageMap = MutableMap<Message, Int>
private typealias ParserMap = MutableMap<Int, MessageParser>

object GlobalProto {
    private val logger = logger()
    private var initDone = false
    private val client2ServerParser: ParserMap = mutableMapOf()
    private val client2ServerMessage: MessageMap = mutableMapOf()
    private val server2ClientParser: ParserMap = mutableMapOf()
    private val server2ClientMessage: MessageMap = mutableMapOf()

    @Synchronized
    fun init(
        clientDescriptors: Descriptors.Descriptor,
        serverDescriptors: Descriptors.Descriptor,
        pkg: String = "com.mikai233.protocol"
    ) {
        if (initDone) {
            logger.info("already init done, ignore")
            return
        }
        val allParsers = scanAllProtoParser(pkg)
        val (clientMessage, clientParser) = generateMessageMap(allParsers, clientDescriptors)
        val (serverMessage, serverParser) = generateMessageMap(allParsers, serverDescriptors)
        client2ServerParser.putAll(clientParser)
        client2ServerMessage.putAll(clientMessage)
        server2ClientParser.putAll(serverParser)
        server2ClientMessage.putAll(serverMessage)
        checkReqResp()
        logger.info("init client message map and server message map done")
        initDone = true
    }

    private fun scanAllProtoParser(pkg: String): Map<Message, MessageParser> {
        val allParsers = mutableMapOf<Message, MessageParser>()
        Reflections(pkg).getSubTypesOf(GeneratedMessage::class.java).forEach { messageClazz ->
            val clazz = messageClazz.kotlin
            val parserFunction = clazz.declaredFunctions.find { it.name == "parser" }
            if (parserFunction == null) {
                logger.warn("parser of proto message:${clazz.qualifiedName} not found")
            } else {
                @Suppress("UNCHECKED_CAST")
                val parser = parserFunction.call() as Parser<out GeneratedMessage>
                allParsers[clazz] = parser
            }
        }
        return allParsers
    }

    private fun generateMessageMap(
        parsers: Map<Message, MessageParser>,
        descriptor: Descriptors.Descriptor
    ): Pair<MessageMap, ParserMap> {
        val messageNumberMap = mutableMapOf<String, Int>()
        val parserMap = mutableMapOf<Int, MessageParser>()
        val messageMap = mutableMapOf<Message, Int>()
        descriptor.fields.forEach {
            val typeName = it.messageType.name
            val number = it.number
            check(messageNumberMap.containsKey(typeName).not()) { "duplicate register message type ${it.fullName}" }
            messageNumberMap[typeName] = number
        }
        messageNumberMap.forEach { (messageSimpleName, number) ->
            val entry =
                requireNotNull(parsers.entries.find { it.key.simpleName == messageSimpleName }) { "proto message:${messageSimpleName} parser not found" }
            parserMap[number] = entry.value
            messageMap[entry.key] = number
        }
        return messageMap to parserMap
    }

    private fun checkReqResp() {
        val incorrectMessage = mutableSetOf<String>()
        client2ServerMessage.forEach { (key, _) ->
            val simpleName = requireNotNull(key.simpleName) { "$key simple name not found" }
            if (simpleName.endsWith("Req").not()) {
                val qualifiedName = requireNotNull(key.qualifiedName) { "$key qualified name not found" }
                incorrectMessage.add(qualifiedName)
            }
        }
        check(incorrectMessage.isEmpty()) { "client to server message:${incorrectMessage} should end with Req" }
        incorrectMessage.clear()
        server2ClientMessage.forEach { (key, _) ->
            val simpleName = kotlin.requireNotNull(key.simpleName) { "$key simple name not found" }
            if ((simpleName.endsWith("Resp") || simpleName.endsWith("Notify")).not()) {
                val qualifiedName = requireNotNull(key.qualifiedName) { "$key qualified name not found" }
                incorrectMessage.add(qualifiedName)
            }
        }
        check(incorrectMessage.isEmpty()) { "server to client message should end with Resp or Notify" }
        val clientReq = client2ServerMessage
        val serverResp = server2ClientMessage.filterKeys {
            val simpleName = kotlin.requireNotNull(it.simpleName) { "$it simple name not found" }
            simpleName.endsWith("Resp")
        }
        val missingResp = clientReq.values - serverResp.values.toSet()
        check(missingResp.isEmpty()) { "client request:${missingResp} has no resp id, make sure req and resp has same id" }
        val missingReq = serverResp.values - clientReq.values.toSet()
        check(missingReq.isEmpty()) { "server response:${missingReq} has no req id, make sure req and resp has same id" }
    }

    fun getClientMessageId(message: Message): Int {
        return requireNotNull(client2ServerMessage[message]) { "client message:${message} proto number not found" }
    }

    fun getClientMessageParser(protoNumber: Int): MessageParser {
        return requireNotNull(client2ServerParser[protoNumber]) { "client proto number:${protoNumber} message parser not found" }
    }

    fun getServerMessageId(message: Message): Int {
        return requireNotNull(server2ClientMessage[message]) { "server message:${message} proto number not found" }
    }

    fun getServerMessageParser(protoNumber: Int): MessageParser {
        return requireNotNull(server2ClientParser[protoNumber]) { "server proto number:${protoNumber} message parser not found" }
    }
}
