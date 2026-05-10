package com.mikai233.common.battle

data class BattleEndpoint(
    val instanceId: String,
    val host: String,
    val port: Int,
    val transport: String = DEFAULT_TRANSPORT,
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(instanceId.isNotBlank()) { "battle endpoint instanceId must not be blank" }
        require(host.isNotBlank()) { "battle endpoint host must not be blank" }
        require(port in 1..65535) { "battle endpoint port out of range: $port" }
        require(transport.isNotBlank()) { "battle endpoint transport must not be blank" }
    }

    companion object {
        const val DEFAULT_TRANSPORT = "tcp-frame"
    }
}
