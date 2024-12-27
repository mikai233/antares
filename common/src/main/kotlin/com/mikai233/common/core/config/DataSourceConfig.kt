package com.mikai233.common.core.config

data class DataSource(
    val host: String,
    val port: Int,
)

data class DataSourceConfig(
    val databaseName: String,
    val sources: List<DataSource>,
)
