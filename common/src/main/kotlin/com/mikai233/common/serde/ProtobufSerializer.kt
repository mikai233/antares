package com.mikai233.common.serde

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.google.protobuf.GeneratedMessage
import com.google.protobuf.Parser
import com.mikai233.protocol.ClientToServerMessageById
import com.mikai233.protocol.ClientToServerParserById
import com.mikai233.protocol.ServerToClientMessageById
import com.mikai233.protocol.ServerToClientParserById

class ProtobufSerializer : Serializer<GeneratedMessage>() {
    private val parserByType: Map<Class<out GeneratedMessage>, Parser<out GeneratedMessage>>

    init {
        val parserByType: MutableMap<Class<out GeneratedMessage>, Parser<out GeneratedMessage>> = mutableMapOf()
        ClientToServerMessageById.forEach { (type, id) ->
            val parser = requireNotNull(ClientToServerParserById[id]) { "Parser for id $id not found" }
            parserByType[type.java] = parser
        }
        ServerToClientMessageById.forEach { (type, id) ->
            val parser = requireNotNull(ServerToClientParserById[id]) { "Parser for type $type not found" }
            parserByType[type.java] = parser
        }
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
