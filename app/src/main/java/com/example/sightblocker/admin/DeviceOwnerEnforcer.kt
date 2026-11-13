package com.example.sightblocker.admin

import com.example.sightblocker.engine.Enforcer

/** Blocks YouTube using Device Owner powers: Chrome policy, app suspension, tamper and clock locks. */
class DeviceOwnerEnforcer(private val policy: PolicyController) : Enforcer {

    override fun isAvailable(): Boolean = policy.isDeviceOwner()

    override fun enforce() {
        policy.applyChromeBlock()
        policy.applyAppBlock()
        policy.applyTamperProtection()
        policy.lockClock()
    }

    override fun release() {
        policy.clearChromeBlock()
        policy.clearAppBlock()
        policy.clearTamperProtection()
        policy.unlockClock()
    }
}
