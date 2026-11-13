package com.example.sightblocker.admin

import android.os.Bundle

/**
 * Builds the managed-configuration bundle Chrome reads as enterprise policy.
 * Pure data, no Android services, so it is easy to extend with more sites later.
 */
object ChromePolicy {
    const val PACKAGE = "com.android.chrome"

    /** Chrome URLBlocklist patterns. "youtube.com" also covers subdomains such as m.youtube.com. */
    val BLOCKED_SITES = listOf(
        "youtube.com",
        "youtu.be",
        "youtube-nocookie.com",
    )

    fun blocking(): Bundle = Bundle().apply {
        putStringArray("URLBlocklist", BLOCKED_SITES.toTypedArray())
        // 1 = Incognito disabled, otherwise it is an easy way around the list in some setups.
        putInt("IncognitoModeAvailability", 1)
    }

    /** Empty bundle removes all restrictions we set. */
    fun unblocked(): Bundle = Bundle()
}
