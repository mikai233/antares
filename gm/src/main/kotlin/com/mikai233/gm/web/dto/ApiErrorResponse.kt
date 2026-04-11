package com.mikai233.gm.web.dto

data class ApiErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
)
