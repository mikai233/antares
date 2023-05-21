package com.mikai233.shared.serde

import akka.actor.ExtendedActorSystem
import akka.actor.typed.ActorRefResolver
import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Adapter
import akka.serialization.JSerializer
import com.mikai233.common.ext.toByteArray
import com.mikai233.common.ext.toShort
import com.mikai233.shared.message.PlayerProtobufEnvelope
import com.mikai233.shared.message.SerdeChannelMessage

class PlayerProtobufSerializer(extSystem: ExtendedActorSystem) : JSerializer() {
    private val system: ActorSystem<Void> = Adapter.toTyped(extSystem)
    private val resolver = ActorRefResolver.get(system)
    override fun fromBinaryJava(bytes: ByteArray, manifest: Class<*>?): Any {
        val refLen = bytes.toShort()
        val serializedRef =
            resolver.resolveActorRef<SerdeChannelMessage>(String(bytes.sliceArray(Short.SIZE_BYTES until (Short.SIZE_BYTES + refLen))))
        val protoMessage = packetToProtoMsg(bytes.sliceArray((Short.SIZE_BYTES + refLen) until bytes.size), true)
        return PlayerProtobufEnvelope(protoMessage, serializedRef)
    }

    override fun identifier(): Int {
        return 247510507
    }

    override fun toBinary(o: Any): ByteArray {
        o as PlayerProtobufEnvelope
        val messageBytes = protoMsgToPacket(o.inner, true)
        val serializedRef = resolver.toSerializationFormat(o.channelActor).toByteArray()
        return serializedRef.size.toShort().toByteArray() + serializedRef + messageBytes
    }

    override fun includeManifest(): Boolean {
        return false
    }
}