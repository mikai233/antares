package com.mikai233.common.serde

import com.esotericsoftware.kryo.kryo5.Serializer
import com.esotericsoftware.kryo.kryo5.serializers.FieldSerializer
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.extension.logger
import com.mikai233.common.message.MessageDeps
import io.altoo.serialization.kryo.pekko.DefaultKryoInitializer
import io.altoo.serialization.kryo.scala.serializer.ScalaKryo

class XKryoInitializer : DefaultKryoInitializer() {
    val logger = logger()

    override fun preInit(kryo: ScalaKryo) {
        super.preInit(kryo)
        @Suppress("UNCHECKED_CAST")
        kryo.setDefaultSerializer(FieldSerializer::class.java as Class<out Serializer<*>>)
        @Suppress("UNCHECKED_CAST")
        kryo.addDefaultSerializer(GeneratedMessage::class.java as Class<Any>, ProtobufSerializer() as Serializer<Any>)
        (MessageDeps + DepsExtra).forEachIndexed { index, clazz ->
            val id = index + 30
            logger.debug("register {} with id:{}", clazz, id)
            kryo.register(clazz.java, id)
        }
    }
}
