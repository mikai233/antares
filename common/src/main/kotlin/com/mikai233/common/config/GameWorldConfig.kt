package com.mikai233.common.config

import kotlinx.datetime.LocalDateTime

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/9
 */
data class GameWorldConfig(
    val id: Long,
    val name: String,
    val openDateTime: String,
    val onlineLimit: Long,
    val registerLimit: Long,
) {
    fun openDateTime() = LocalDateTime.parse(openDateTime, LocalDateTime.Formats.ISO)
}
