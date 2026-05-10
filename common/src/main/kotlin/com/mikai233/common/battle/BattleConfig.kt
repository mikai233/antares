package com.mikai233.common.battle

import com.typesafe.config.Config
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toKotlinDuration

data class BattleConfig(
    val endpoints: List<BattleEndpoint>,
    val tokenSecret: String,
    val tokenTtl: Duration,
) {
    companion object {
        fun load(config: Config): BattleConfig {
            val endpoints = if (config.hasPath("game.battle.endpoints")) {
                config.getConfigList("game.battle.endpoints").map { endpoint ->
                    BattleEndpoint(
                        instanceId = endpoint.getString("instance-id"),
                        host = endpoint.getString("host"),
                        port = endpoint.getInt("port"),
                        transport = if (endpoint.hasPath("transport")) {
                            endpoint.getString("transport")
                        } else {
                            BattleEndpoint.DEFAULT_TRANSPORT
                        },
                    )
                }
            } else {
                emptyList()
            }
            return BattleConfig(
                endpoints = endpoints,
                tokenSecret = if (config.hasPath("game.battle.token-secret")) {
                    config.getString("game.battle.token-secret")
                } else {
                    DEFAULT_TOKEN_SECRET
                },
                tokenTtl = if (config.hasPath("game.battle.token-ttl")) {
                    config.getDuration("game.battle.token-ttl").toKotlinDuration()
                } else {
                    DEFAULT_TOKEN_TTL
                },
            )
        }

        const val DEFAULT_TOKEN_SECRET = "local-dev-battle-secret"
        val DEFAULT_TOKEN_TTL: Duration = 5.minutes
    }
}
