package com.mikai233.common.crypto

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class AESCipher(val key: ByteArray) {
    constructor(key: String) : this(key.toByteArray())

    private val encryptCipher: Cipher = Cipher.getInstance("AES")
    private val decryptCipher: Cipher = Cipher.getInstance("AES")

    init {
        val key = SecretKeySpec(key, "AES")
        encryptCipher.init(Cipher.ENCRYPT_MODE, key)
        decryptCipher.init(Cipher.DECRYPT_MODE, key)
    }

    fun encrypt(data: ByteArray): ByteArray {
        return encryptCipher.doFinal(data)
    }

    fun decrypt(data: ByteArray): ByteArray {
        return decryptCipher.doFinal(data)
    }
}
