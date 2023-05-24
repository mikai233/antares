package com.mikai233.common.excel

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

interface ExcelSerde {
    fun ser(config: ExcelConfig): ByteArray
    fun de(bytes: ByteArray): ExcelConfig
}

class JsonExcelSerde : ExcelSerde {
    private val mapper = jacksonObjectMapper()
    private val prettyWriter = mapper.writerWithDefaultPrettyPrinter()
    override fun ser(config: ExcelConfig): ByteArray {
        return prettyWriter.writeValueAsBytes(config)
    }

    override fun de(bytes: ByteArray): ExcelConfig {
        return mapper.readValue(bytes)
    }
}