package com.mikai233.common.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Key
import java.security.SecureRandom
import java.security.Security

data class KeyPair(val privateKey: ByteArray, val publicKey: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyPair

        if (!privateKey.contentEquals(other.privateKey)) return false
        return publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int {
        var result = privateKey.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        return result
    }
}

object ECDH {
    init {
        Security.addProvider(BouncyCastleProvider())
    }

    private val secureRandom = SecureRandom()

    fun calculateShareKey(selfPrivateKeyBytes: ByteArray, remotePublicKeyBytes: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        val result = ByteArray(agreement.agreementSize)
        agreement.init(X25519PrivateKeyParameters(selfPrivateKeyBytes))
        agreement.calculateAgreement(X25519PublicKeyParameters(remotePublicKeyBytes), result, 0)
        return result
    }

    fun genKeyPair(): KeyPair {
        val private = X25519PrivateKeyParameters(secureRandom)
        val privateKey = private.encoded
        val publicKey = private.generatePublicKey().encoded
        return KeyPair(privateKey, publicKey)
    }

    fun debugKey(key: Key): String {
        return key.encoded.contentToString()
    }
}