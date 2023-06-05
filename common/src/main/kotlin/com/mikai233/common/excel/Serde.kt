package com.mikai233.common.excel

import kotlinx.serialization.Serializable


@Serializable
data class SerdeConfigs(val configs: Set<SerdeConfig>)
interface ExcelSerde {
    fun ser(configs: SerdeConfigs): ByteArray

    fun de(bytes: ByteArray): SerdeConfigs
}
