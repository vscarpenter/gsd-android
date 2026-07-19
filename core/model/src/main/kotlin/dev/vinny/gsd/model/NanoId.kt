package dev.vinny.gsd.model

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Client-side ID generation (spec §7.1): URL-safe nanoid alphabet, uniform random.
 * Lengths in use: tasks/subtasks 21, time entries 8, smart views 12.
 */
object NanoId {
    const val ALPHABET: String =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private val secure: Random = SecureRandom().asKotlinRandom()

    fun generate(length: Int = 21, random: Random = secure): String =
        buildString(length) { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
}
