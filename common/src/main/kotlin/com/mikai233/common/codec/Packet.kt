package com.mikai233.common.codec

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.util.ReferenceCounted

/**
 * @param protoId proto number max value:536,870,911
 * @param originLen
 */
class Packet(
    val protoId: Int,
    val originLen: Int,
    val body: ByteBuf,
) : ReferenceCounted {
    override fun refCnt(): Int = body.refCnt()

    override fun retain(): Packet {
        body.retain()
        return this
    }

    override fun retain(increment: Int): Packet {
        body.retain(increment)
        return this
    }

    override fun touch(): Packet {
        body.touch()
        return this
    }

    override fun touch(hint: Any?): Packet {
        body.touch(hint)
        return this
    }

    override fun release(): Boolean = body.release()

    override fun release(decrement: Int): Boolean = body.release(decrement)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Packet

        if (protoId != other.protoId) return false
        if (originLen != other.originLen) return false
        if (!ByteBufUtil.equals(body, other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = protoId
        result = 31 * result + originLen
        result = 31 * result + ByteBufUtil.hashCode(body)
        return result
    }
}
