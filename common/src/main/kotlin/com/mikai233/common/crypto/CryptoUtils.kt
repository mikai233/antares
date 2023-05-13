package com.mikai233.common.crypto

import com.mikai233.common.ext.toUHexString
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*
import kotlin.random.Random
import kotlin.random.nextInt

object CryptoUtils {
    private val sha256Digest: MessageDigest = MessageDigest.getInstance("SHA256")
    private val md5Digest: MessageDigest = MessageDigest.getInstance("MD5")

    fun String.hash(): ByteArray {
        return sha256Digest.digest(toByteArray())
    }

    fun ByteArray.hash(): ByteArray {
        return sha256Digest.digest(this)
    }

    fun String.hashWithSalt(saltNum: Int = 16): Pair<String, ByteArray> {
        return toByteArray().hashWithSalt(saltNum)
    }

    fun String.hashWithSalt(salt: String): ByteArray {
        val re = hash() + salt.hash()
        return re.hash()
    }

    fun ByteArray.hashWithSalt(saltNum: Int = 16): Pair<String, ByteArray> {
        val salt = genSalt(saltNum)
        val re = hash() + salt.hash()
        return salt to re.hash()
    }

    fun ByteArray.hashWithSalt(salt: String): ByteArray {
        val re = hash() + salt.hash()
        return re.hash()
    }

    fun genSalt(num: Int): String {
        val chars = mutableListOf<Char>()
        repeat(num) {
            Random.nextInt(33..126).toChar().let {
                chars.add(it)
            }
        }
        return chars.joinToString("")
    }

    fun String.md5(): ByteArray {
        return md5Digest.digest(toByteArray())
    }

    fun ByteArray.md5(): ByteArray {
        return md5Digest.digest(this)
    }

    fun String.simpleMD5(): String {
        return md5().toUHexString("").lowercase()
    }

    fun ByteArray.simpleMD5(): String {
        return md5().toUHexString("").lowercase()
    }

    fun String.base64Decode(): ByteArray {
        return Base64.getDecoder().decode(this)
    }

    fun String.base64DecodeToString(): String {
        return base64Decode().decodeToString()
    }

    fun ByteArray.base64Decode(): ByteArray {
        return Base64.getDecoder().decode(this)
    }

    fun ByteArray.base64DecodeToString(): String {
        return base64Decode().decodeToString()
    }

    fun String.base64(): String {
        return Base64.getEncoder().encodeToString(toByteArray())
    }

    fun ByteArray.base64(): String {
        return Base64.getEncoder().encodeToString(this)
    }

    fun publicKeyFromBytes(bytes: ByteArray): PublicKey {
        return KeyFactory.getInstance("ECDH").generatePublic(X509EncodedKeySpec(bytes))
    }
}