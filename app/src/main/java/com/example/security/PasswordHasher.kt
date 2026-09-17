package com.example.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Password hashing using PBKDF2-HMAC-SHA256 with a unique random salt. */
object PasswordHasher {
    private const val SALT_LENGTH = 16
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256

    fun generateSalt(): String {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }

    fun hashPassword(password: String, salt: String): String {
        require(password.isNotEmpty()) { "Password must not be empty" }
        val saltBytes = salt.hexToBytes()
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH)
        return try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            key.joinToString("") { "%02x".format(it) }
        } finally {
            (spec as PBEKeySpec).clearPassword()
        }
    }

    fun verifyPassword(password: String, salt: String, expectedHash: String): Boolean {
        return try {
            val computed = hashPassword(password, salt)
            MessageDigest.isEqual(
                computed.hexToBytes(),
                expectedHash.hexToBytes()
            )
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hex value" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
