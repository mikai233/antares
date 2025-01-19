package com.mikai233.common.serde

import akka.actor.ExtendedActorSystem
import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.extension.logger
import com.mikai233.common.message.MessageDeps
import io.altoo.akka.serialization.kryo.DefaultKryoInitializer
import io.altoo.akka.serialization.kryo.serializer.scala.ScalaKryo

class XKryoInitializer : DefaultKryoInitializer() {
    val logger = logger()

    override fun preInit(kryo: ScalaKryo, system: ExtendedActorSystem) {
        kryo.setDefaultSerializer(FieldSerializer::class.java)
        kryo.addDefaultSerializer(GeneratedMessage::class.java, ProtobufSerializer())
        (MessageDeps + DepsExtra).forEachIndexed { index, clazz ->
            val id = index + 30
            logger.debug("register {} with id:{}", clazz, id)
            kryo.register(clazz.java, id)
        }
    }
}
