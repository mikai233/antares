package com.mikai233.tools.excel

import com.mikai233.shared.excel.configSerdeModule
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf

@OptIn(ExperimentalSerializationApi::class)
val format = ProtoBuf { serializersModule = configSerdeModule }

object ExcelBinGenerator {
    @JvmStatic
    fun main(args: Array<String>) {

    }
}