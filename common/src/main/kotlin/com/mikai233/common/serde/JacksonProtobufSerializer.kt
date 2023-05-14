package com.mikai233.common.serde

import akka.actor.ExtendedActorSystem
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorRefResolver
import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Adapter
import akka.serialization.JSerializer
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonTokenId
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer
import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.mikai233.common.ext.logger
import com.mikai233.common.msg.Message
import kotlin.reflect.KClass

object JacksonProtobufSerdeMessage {
    val logger = logger()
    private var isInitialized = false
    private val Messages: MutableList<KClass<out Message>> = mutableListOf()


    @Synchronized
    fun init(messages: List<KClass<out Message>>) {
        if (isInitialized) {
            logger.info("already initialized, ignore")
            return
        }
        isInitialized = true
        Messages.addAll(messages)
    }

    fun getMessages(): List<KClass<out Message>> = Messages
}

class JacksonProtobufSerializer(private val system: ExtendedActorSystem) : JSerializer() {
    private val mapper = ProtobufMapper().apply {
        registerKotlinModule()
        registerModule(TypedActorRefModule(Adapter.toTyped(system)))
    }
    private val schemas: HashMap<KClass<out Message>, ProtobufSchema> = hashMapOf()
    private val writers: HashMap<String, ObjectWriter> = hashMapOf()
    private val readers: HashMap<String, ObjectReader> = hashMapOf()

    init {
        init(JacksonProtobufSerdeMessage.getMessages())
    }

    fun init(messages: List<KClass<out Message>>) {
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

    private fun getWriter(name: String): ObjectWriter {
        return requireNotNull(writers[name]) { "no writer found with name:$name" }
    }

    private fun getReader(name: String): ObjectReader {
        return requireNotNull(readers[name]) { "no reader found with name:$name" }
    }

    override fun fromBinaryJava(bytes: ByteArray, manifest: Class<*>): Any {
        val name = manifest.name
        val reader = getReader(name)
        return reader.readValue<InternalMessage>(bytes)
    }

    override fun identifier(): Int {
        return 1876383642
    }

    override fun toBinary(o: Any): ByteArray {
        val name = o.javaClass.name
        val writer = getWriter(name)
        return writer.writeValueAsBytes(o)
    }

    override fun includeManifest(): Boolean {
        return true
    }
}

class TypedActorRefDeserializer(system: ActorSystem<Void>) :
    StdScalarDeserializer<ActorRef<*>>(ActorRef::class.java) {
    private val resolver: ActorRefResolver = ActorRefResolver.get(system)
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ActorRef<*> {
        return if (p.currentTokenId() == JsonTokenId.ID_STRING) {
            val serializedActorRef = p.text
            resolver.resolveActorRef<ActorRef<*>>(serializedActorRef)
        } else {
            ctxt.handleUnexpectedToken(handledType(), p) as ActorRef<*>
        }
    }
}

class TypedActorRefSerializer(system: ActorSystem<Void>) :
    StdScalarSerializer<ActorRef<*>>(ActorRef::class.java) {
    private val resolver: ActorRefResolver = ActorRefResolver.get(system)

    override fun serialize(value: ActorRef<*>, gen: JsonGenerator, provider: SerializerProvider) {
        val serializedActorRef = resolver.toSerializationFormat(value)
        gen.writeString(serializedActorRef)
    }
}

class TypedActorRefModule(system: ActorSystem<Void>) : SimpleModule("TypedActorRefModule") {
    init {
        addSerializer(TypedActorRefSerializer(system))
        addDeserializer(ActorRef::class.java, TypedActorRefDeserializer(system))
    }
}