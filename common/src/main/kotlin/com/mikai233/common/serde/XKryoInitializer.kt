package com.mikai233.common.serde

import akka.actor.ExtendedActorSystem
import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.mikai233.common.extension.logger
import com.mikai233.common.message.Message
import io.altoo.akka.serialization.kryo.DefaultKryoInitializer
import io.altoo.akka.serialization.kryo.serializer.scala.ScalaKryo
import org.reflections.Reflections

object KryoMapper {
    val mapper: Map<Int, Class<out Message>>

    init {
        val allInternalMessages =
            Reflections("com.mikai233.common.message")
                .getSubTypesOf(Message::class.java)
                .asSequence()
                .filter { !it.isInterface }
                .sortedBy { it.name }
                .mapIndexed { index, clazz -> (index + 30) to clazz }
                .associate { it }
        mapper = allInternalMessages
    }
}

class XKryoInitializer : DefaultKryoInitializer() {
    val logger = logger()
    override fun preInit(kryo: ScalaKryo, system: ExtendedActorSystem) {
        kryo.setDefaultSerializer(FieldSerializer::class.java)
        KryoMapper.mapper.forEach { (id, clazz) ->
            logger.debug("register {} with id:{}", clazz, id)
            kryo.register(clazz, id)
        }
    }
}
