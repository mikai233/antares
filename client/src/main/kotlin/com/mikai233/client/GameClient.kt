package com.mikai233.client

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.common.io.Resources
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.Message
import com.google.protobuf.kotlin.toByteString
import com.google.protobuf.util.JsonFormat
import com.mikai233.common.conf.GlobalData
import com.mikai233.common.conf.GlobalProto
import com.mikai233.common.crypto.ECDH
import com.mikai233.common.crypto.KeyPair
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tryCatch
import com.mikai233.protocol.MsgCs.MessageClientToServer
import com.mikai233.protocol.MsgSc
import com.mikai233.protocol.loginReq
import io.netty.channel.Channel
import io.netty.util.AttributeKey
import org.reflections.Reflections
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions

class GameClient(host: String, port: Int) {
    private val logger = logger()
    private val client = NettyClient(host, port, ClientChannelInitializer())
    private val keyPair = ECDH.genKeyPair()

    companion object {
        val key: AttributeKey<KeyPair> = AttributeKey.valueOf("KEY_PAIR")
    }

    init {
        GlobalProto.init(MessageClientToServer.getDescriptor(), MsgSc.MessageServerToClient.getDescriptor())
    }

    fun start() {
        val clientYaml = Resources.getResource("client.yaml")
        val mapper = YAMLMapper().registerKotlinModule()
        val clientInfo = mapper.readValue<ClientInfo>(clientYaml)
        val loginReq = loginReq {
            account = clientInfo.account
            worldId = clientInfo.worldId
            clientPublicKey = keyPair.publicKey.toByteString()
            clientZone = GlobalData.zoneId.toString()
        }
        genMessageToBuilder()
        val channel = client.startClient().sync().channel()
        channel.attr(key).set(keyPair)
        channel.writeAndFlush(loginReq)
        handleInput(channel)
    }

    private fun handleInput(channel: Channel) {
        val scanner = Scanner(System.`in`)
        val requestRegex = Regex("\\$(\\w+) (.*)")
        val messageToBuilder = genMessageToBuilder()
        val parser = JsonFormat.parser()
        while (scanner.hasNextLine()) {
            val cmd = scanner.nextLine()
            if (cmd.startsWith("$")) {
                //request
                requestRegex.find(cmd)?.let {
                    val requestName = it.groups[1]!!.value
                    val requestJson = it.groups[2]!!.value
                    val kFunction = messageToBuilder[requestName]
                    if (kFunction == null) {
                        logger.warn("message name:{} not associated to protobuf message", requestName)
                    } else {
                        val builder = kFunction.call() as Message.Builder
                        tryCatch(logger) {
                            parser.merge(requestJson, builder)
                            val req = builder.build()
                            channel.writeAndFlush(req)
                        }
                    }
                }
            } else {
                //gm
            }
        }
    }

    private fun genMessageToBuilder(): MutableMap<String, KFunction<*>> {
        val messageToBuilder = mutableMapOf<String, KFunction<*>>()
        Reflections("com.mikai233.protocol").getSubTypesOf(GeneratedMessageV3::class.java).forEach { messageClazz ->
            val clazz = messageClazz.kotlin
            val builderFunction =
                requireNotNull(clazz.declaredFunctions.find { it.name == "newBuilder" && it.parameters.isEmpty() }) { "$clazz newBuilder not found" }
            val protoName = requireNotNull(clazz.simpleName) { "$clazz simple name not found" }
            messageToBuilder[protoName] = builderFunction
        }
        return messageToBuilder
    }
}
