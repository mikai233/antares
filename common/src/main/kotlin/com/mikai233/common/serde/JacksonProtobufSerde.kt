package com.mikai233.common.serde

import akka.serialization.JSerializer
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlin.reflect.KClass

object JacksonProtobufMeta {
    private val mapper = ProtobufMapper().apply {
        registerKotlinModule()
    }
    private val schemas: HashMap<KClass<out InternalMessage>, ProtobufSchema> = hashMapOf()
    private val writers: HashMap<String, ObjectWriter> = hashMapOf()
    private val readers: HashMap<String, ObjectReader> = hashMapOf()

    fun init(messages: List<KClass<out InternalMessage>>) {
        messages.forEach { message ->
            val clazz = message.java
            val schema = mapper.generateSchemaFor(clazz)
            schemas[message] = schema
            val writer = mapper.writer(schema)
            writers[clazz.name] = writer
            val reader = mapper.readerFor(clazz).with(schema)
            readers[clazz.name] = reader
        }
    }

    fun getWriter(name: String): ObjectWriter {
        return requireNotNull(writers[name]) { "no writer found with name:$name" }
    }

    fun getReader(name: String): ObjectReader {
        return requireNotNull(readers[name]) { "no reader found with name:$name" }
    }
}

class JacksonProtobufSerde : JSerializer() {

    override fun fromBinaryJava(bytes: ByteArray, manifest: Class<*>): Any {
        val name = manifest.name
        val reader = JacksonProtobufMeta.getReader(name)
        return reader.readValue<InternalMessage>(bytes)
    }

    override fun identifier(): Int {
        return 1876383642
    }

    override fun toBinary(o: Any): ByteArray {
        val name = o.javaClass.name
        val writer = JacksonProtobufMeta.getWriter(name)
        return writer.writeValueAsBytes(o)
    }

    override fun includeManifest(): Boolean {
        return true
    }
}