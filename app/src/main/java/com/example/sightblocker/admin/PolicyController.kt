package com.example.sightblocker.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.util.Log

/** Thin wrapper over DevicePolicyManager. Every call requires this app to be Device Owner. */
class PolicyController(private val context: Context) {

    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, SightBlockerAdminReceiver::class.java)

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    fun isChromeInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(ChromePolicy.PACKAGE, 0)
        true
    } catch (e: Exception) {
        false
    }

    /** Pushes the YouTube blocklist to Chrome. */
    fun applyChromeBlock() {
        dpm.setApplicationRestrictions(admin, ChromePolicy.PACKAGE, ChromePolicy.blocking())
        Log.i(TAG, "Chrome blocklist applied: ${ChromePolicy.BLOCKED_SITES}")
    }

    fun clearChromeBlock() {
        dpm.setApplicationRestrictions(admin, ChromePolicy.PACKAGE, ChromePolicy.unblocked())
        Log.i(TAG, "Chrome blocklist cleared")
    }

    /** Suspends the YouTube app: icon greyed out, cannot be launched, no notifications. */
    fun applyAppBlock() {
        val failed = dpm.setPackagesSuspended(admin, BlockedApps.PACKAGES.toTypedArray(), true)
        Log.i(TAG, "App suspend requested ${BlockedApps.PACKAGES}, failed=${failed.toList()}")
    }

    fun clearAppBlock() {
        val failed = dpm.setPackagesSuspended(admin, BlockedApps.PACKAGES.toTypedArray(), false)
        Log.i(TAG, "App unsuspend requested, failed=${failed.toList()}")
    }

    /** Packages from [BlockedApps] that are installed and currently suspended. */
    fun suspendedApps(): List<String> = BlockedApps.PACKAGES.filter {
        try {
            dpm.isPackageSuspended(admin, it)
        } catch (e: Exception) {
            false // not installed
        }
    }

    /** What Chrome has currently been given, as read back from the system. */
    fun currentChromeRestrictions(): String {
        val b = dpm.getApplicationRestrictions(admin, ChromePolicy.PACKAGE)
        if (b.isEmpty) return "(none)"
        return b.keySet().joinToString { key ->
            val v = b.get(key)
            "$key=" + if (v is Array<*>) v.joinToString(prefix = "[", postfix = "]") else v.toString()
        }
    }

    /**
     * Anti-tamper protection for THIS app only: it cannot be uninstalled, force-stopped,
     * or have its data cleared. Other apps are unaffected.
     */
    fun applyTamperProtection() {
        dpm.setUninstallBlocked(admin, context.packageName, true)
        dpm.setUserControlDisabledPackages(admin, listOf(context.packageName))
        Log.i(TAG, "Tamper protection applied (own package only)")
    }

    fun clearTamperProtection() {
        dpm.setUninstallBlocked(admin, context.packageName, false)
        dpm.setUserControlDisabledPackages(admin, emptyList())
        // Also clears restrictions an earlier build set device-wide.
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
        Log.i(TAG, "Tamper protection cleared")
    }

    /**
     * Stops the user changing the date, time or time zone, and forces automatic network time.
     * This is what makes the unlock wait hard to cheat by moving the clock.
     */
    fun lockClock() {
        dpm.setAutoTimeEnabled(admin, true)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME)
        Log.i(TAG, "Clock locked")
    }

    fun unlockClock() {
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME)
        Log.i(TAG, "Clock unlocked")
    }

    /** DEV ESCAPE HATCH: gives up Device Owner so a dev build can be uninstalled normally. */
    @Suppress("DEPRECATION")
    fun releaseDeviceOwner() {
        clearChromeBlock()
        clearAppBlock()
        unlockClock()
        clearTamperProtection()
        dpm.clearDeviceOwnerApp(context.packageName)
        Log.w(TAG, "Device owner released")
    }

    companion object {
        private const val TAG = SightBlockerAdminReceiver.TAG
    }
}
