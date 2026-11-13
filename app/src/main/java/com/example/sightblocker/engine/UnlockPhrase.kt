package com.example.sightblocker.engine

/**
 * The phrase typed to ask for an unblock, and typed again after the wait to confirm it.
 * It is not a secret. Its only job is to add friction. Change it here.
 */
object UnlockPhrase {
    const val TEXT = "I declare on my honor that I wish to use internet for honest reasons"

    /** Exact match (case and punctuation count). Leading and trailing spaces are ignored. */
    fun matches(input: String): Boolean = input.trim() == TEXT
}
