package com.mikai233.shared.serde

import com.mikai233.common.excel.ExcelSerde
import com.mikai233.common.excel.SerdeConfigs
import com.mikai233.common.ext.toByteArray
import com.mikai233.common.ext.toInt
import com.mikai233.shared.excel.configSerdeModule
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import net.jpountz.lz4.LZ4Compressor
import net.jpountz.lz4.LZ4Factory
import net.jpountz.lz4.LZ4FastDecompressor

object ProtobufExcelSerde : ExcelSerde {
    @OptIn(ExperimentalSerializationApi::class)
    val format = ProtoBuf { serializersModule = configSerdeModule }

    @OptIn(ExperimentalSerializationApi::class)
    override fun ser(configs: SerdeConfigs): ByteArray {
        val protoBytes = format.encodeToByteArray(configs)
        val originSize = protoBytes.size
        val compressed = compressor().compress(protoBytes)
        return originSize.toByteArray() + compressed
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun de(bytes: ByteArray): SerdeConfigs {
        val originSize = bytes.toInt()
        val decompressedBytes = ByteArray(originSize)
        decompressor().decompress(bytes.sliceArray(Int.SIZE_BYTES until bytes.size), decompressedBytes)
        return format.decodeFromByteArray(decompressedBytes)
    }

    private fun compressor(): LZ4Compressor {
        return LZ4Factory.fastestInstance().fastCompressor()
    }

    private fun decompressor(): LZ4FastDecompressor {
        return LZ4Factory.fastestInstance().fastDecompressor()
    }
}