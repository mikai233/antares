package com.mikai233.common.config

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class MongoDeploymentMode {
    Standalone,
    ReplicaSet,
    ShardedCluster,
}

data class MongoEndpoint(
    val host: String,
    val port: Int,
) {
    init {
        require(host.isNotBlank()) { "Mongo endpoint host must not be blank" }
        require(port in 1..65535) { "Mongo endpoint port must be in 1..65535" }
    }
}

data class MongoValidationConfig(
    val enabled: Boolean = true,
    val ping: Boolean = true,
    val requiredCollections: List<String> = emptyList(),
    val ensureIndexes: Boolean = false,
)

data class DataSourceConfig(
    val databaseName: String,
    val endpoints: List<MongoEndpoint>,
    val mode: MongoDeploymentMode = MongoDeploymentMode.Standalone,
    val replicaSetName: String? = null,
    val authDatabase: String? = null,
    val username: String? = null,
    val passwordEnv: String? = null,
    val readPreference: String? = null,
    val writeConcern: String = "majority",
    val validation: MongoValidationConfig = MongoValidationConfig(),
) {
    init {
        require(databaseName.isNotBlank()) { "Mongo databaseName must not be blank" }
        require(endpoints.isNotEmpty()) { "Mongo endpoints must not be empty" }
        require(mode != MongoDeploymentMode.ReplicaSet || !replicaSetName.isNullOrBlank()) {
            "Mongo replicaSetName is required for ReplicaSet mode"
        }
    }
}

fun DataSourceConfig.mongoUri(passwordProvider: (String) -> String? = System::getenv): String {
    val credentials = mongoCredentials(passwordProvider)?.let { "$it@" }.orEmpty()
    val hosts = endpoints.joinToString(",") { "${it.host}:${it.port}" }
    val options = mongoUriOptions()
    return "mongodb://$credentials$hosts/$databaseName$options"
}

private fun DataSourceConfig.mongoCredentials(passwordProvider: (String) -> String?): String? {
    val user = username?.takeIf(String::isNotBlank) ?: return null
    val passwordEnvName = passwordEnv?.takeIf(String::isNotBlank)
        ?: error("Mongo passwordEnv is required when username is set")
    val password = passwordProvider(passwordEnvName)?.takeIf(String::isNotBlank)
        ?: error("Mongo password environment variable $passwordEnvName is not set")
    return "${urlEncode(user)}:${urlEncode(password)}"
}

private fun DataSourceConfig.mongoUriOptions(): String {
    val options = linkedMapOf<String, String>()
    if (!authDatabase.isNullOrBlank()) {
        options["authSource"] = authDatabase
    }
    if (!readPreference.isNullOrBlank()) {
        options["readPreference"] = readPreference
    }
    if (mode == MongoDeploymentMode.ReplicaSet) {
        options["replicaSet"] = requireNotNull(replicaSetName)
    }
    writeConcernQueryValue()?.let { options["w"] = it }
    if (options.isEmpty()) {
        return ""
    }
    return options.entries.joinToString(prefix = "?", separator = "&") { (name, value) ->
        "${urlEncode(name)}=${urlEncode(value)}"
    }
}

private fun DataSourceConfig.writeConcernQueryValue(): String? {
    return when (val concern = writeConcern.trim()) {
        "", "default" -> null
        "w1", "W1" -> "1"
        else -> concern
    }
}

private fun urlEncode(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8)
}
