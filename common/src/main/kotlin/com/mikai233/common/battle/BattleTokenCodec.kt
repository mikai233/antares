package com.mikai233.common.battle

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class BattleTokenCodec(
    private val secret: String,
) {
    init {
        require(secret.isNotBlank()) { "battle token secret must not be blank" }
    }

    fun issue(
        battleId: Long,
        playerId: Long,
        expiresAtMillis: Long,
    ): String {
        require(battleId > 0) { "battleId must be positive" }
        require(playerId > 0) { "playerId must be positive" }
        require(expiresAtMillis > 0) { "expiresAtMillis must be positive" }
        val body = "$VERSION.$battleId.$playerId.$expiresAtMillis"
        return "$body.${sign(body)}"
    }

    private fun sign(body: String): String {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), HMAC_SHA256))
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(mac.doFinal(body.toByteArray(StandardCharsets.UTF_8)))
    }

    companion object {
        private const val VERSION = "v1"
        private const val HMAC_SHA256 = "HmacSHA256"
    }
}
