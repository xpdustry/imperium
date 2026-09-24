// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.string

import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

@JvmInline
value class Password(val value: String) {
    override fun toString() = "Password(***)"

    companion object {
        // Ambiguous characters such as 0/O and 1/l/I are excluded since generated passwords are read by humans
        private const val LOWERCASE = "abcdefghijkmnopqrstuvwxyz"
        private const val UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        private const val DIGITS = "23456789"
        private const val SYMBOLS = "!#%&*+-=?@_"
        private val random = SecureRandom()

        /** Generates a random password satisfying [DEFAULT_PASSWORD_REQUIREMENTS]. */
        fun random(length: Int = 16): Password {
            require(length >= 8) { "length must be at least 8" }
            val pools = listOf(LOWERCASE, UPPERCASE, DIGITS, SYMBOLS)
            val all = pools.joinToString("")
            val chars = pools.map { it[random.nextInt(it.length)] }.toMutableList()
            repeat(length - chars.size) { chars += all[random.nextInt(all.length)] }
            chars.shuffle(random)
            return Password(chars.joinToString(""))
        }
    }
}

class HashedPassword(val hash: ByteArray, val salt: ByteArray) {}

object ImperiumArgon2 {
    private val random = SecureRandom()

    suspend fun create(password: Password, salt: ByteArray = salt()): HashedPassword =
        withContext(Dispatchers.IO) {
            val generator = generator(salt)
            val result = ByteArray(64)
            generator.generateBytes(password.value.toByteArray(), result)
            return@withContext HashedPassword(result, salt)
        }

    suspend fun equals(password: Password, hash: ByteArray, salt: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            val generator = generator(salt)
            val result = ByteArray(64)
            generator.generateBytes(password.value.toByteArray(), result)
            return@withContext timeConstantEquals(result, hash)
        }

    private fun salt() = ByteArray(64).apply(random::nextBytes)

    private fun generator(salt: ByteArray) =
        Argon2BytesGenerator().apply {
            init(
                Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withIterations(3)
                    .withMemoryAsKB(64 * 1024)
                    .withParallelism(2)
                    .withSalt(salt)
                    .build()
            )
        }

    // Stolen from password4j
    private fun timeConstantEquals(a: ByteArray, b: ByteArray): Boolean {
        var diff = a.size xor b.size
        var i = 0
        while (i < a.size && i < b.size) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
            i++
        }
        return diff == 0
    }
}
