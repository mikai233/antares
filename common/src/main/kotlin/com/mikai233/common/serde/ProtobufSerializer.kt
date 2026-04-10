package com.mikai233.common.serde

import com.esotericsoftware.kryo.kryo5.Kryo
import com.esotericsoftware.kryo.kryo5.Serializer
import com.esotericsoftware.kryo.kryo5.io.Input
import com.esotericsoftware.kryo.kryo5.io.Output
import com.google.protobuf.GeneratedMessage
import com.google.protobuf.Parser
import com.mikai233.protocol.registerClientParsersByType
import com.mikai233.protocol.registerServerParsersByType

class ProtobufSerializer : Serializer<GeneratedMessage>() {
    private val parserByType: Map<Class<out GeneratedMessage>, Parser<out GeneratedMessage>>

    init {
        val parserByType: MutableMap<Class<out GeneratedMessage>, Parser<out GeneratedMessage>> = mutableMapOf()
        registerClientParsersByType(parserByType)
        registerServerParsersByType(parserByType)
        this.parserByType = parserByType
    }

    override fun write(kryo: Kryo, output: Output, `object`: GeneratedMessage) {
        kryo.writeObject(output, `object`.toByteArray())
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out GeneratedMessage>): GeneratedMessage {
        val bytes = kryo.readObject(input, ByteArray::class.java)
        val parser = requireNotNull(parserByType[type]) { "Parser for type $type not found" }
        return parser.parseFrom(bytes)
    }
}
