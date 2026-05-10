package com.mikai233.common.config

import kotlinx.datetime.LocalDateTime

data class GameWorldConfig(
    val id: Long,
    val name: String,
    val openDateTime: String,
    val onlineLimit: Long,
    val registerLimit: Long,
) {
    fun openDateTime() = LocalDateTime.parse(openDateTime, LocalDateTime.Formats.ISO)
}
