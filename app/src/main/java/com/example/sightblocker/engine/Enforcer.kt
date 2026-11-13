package com.example.sightblocker.engine

/**
 * The mechanism that actually blocks things. The engine does not care how.
 * Today: Device Owner policy. Later, maybe: a DNS filter.
 */
interface Enforcer {
    /** False if this mechanism cannot work right now (e.g. not Device Owner). */
    fun isAvailable(): Boolean

    /** Turn blocking on. Must be safe to call repeatedly. */
    fun enforce()

    /** Turn blocking off. Must be safe to call repeatedly. */
    fun release()
}
