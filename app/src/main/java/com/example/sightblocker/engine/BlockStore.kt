package com.example.sightblocker.engine

import android.content.Context

/** The only two facts the engine needs to remember. */
interface BlockStore {
    /** True while the block is on. */
    var locked: Boolean

    /** Wall-clock time (ms) when an unblock was requested, or 0 if none is pending. */
    var unlockRequestedAt: Long
}

/**
 * Backed by SharedPreferences with synchronous commits, so state is on disk before we return.
 * (Chosen over DataStore: no extra dependency for two values. Easy to swap, it is behind [BlockStore].)
 */
class PrefsBlockStore(context: Context) : BlockStore {
    private val prefs = context.applicationContext
        .getSharedPreferences("sightblocker_state", Context.MODE_PRIVATE)

    override var locked: Boolean
        get() = prefs.getBoolean(KEY_LOCKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_LOCKED, value).commit()
        }

    override var unlockRequestedAt: Long
        get() = prefs.getLong(KEY_REQUESTED_AT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_REQUESTED_AT, value).commit()
        }

    private companion object {
        const val KEY_LOCKED = "locked"
        const val KEY_REQUESTED_AT = "unlock_requested_at"
    }
}
