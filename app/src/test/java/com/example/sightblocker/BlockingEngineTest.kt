package com.example.sightblocker

import com.example.sightblocker.engine.BlockStore
import com.example.sightblocker.engine.BlockingEngine
import com.example.sightblocker.engine.Enforcer
import com.example.sightblocker.engine.Outcome
import com.example.sightblocker.engine.Status
import com.example.sightblocker.engine.UnlockPhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BlockingEngineTest {

    private class FakeStore : BlockStore {
        override var locked = false
        override var unlockRequestedAt = 0L
    }

    private class FakeEnforcer(var available: Boolean = true) : Enforcer {
        var enforced = false
        var failOnRelease = false
        override fun isAvailable() = available
        override fun enforce() { enforced = true }
        override fun release() {
            if (failOnRelease) error("boom")
            enforced = false
        }
    }

    private val minute = 60_000L
    private var clock = 1_000_000L
    private lateinit var store: FakeStore
    private lateinit var enforcer: FakeEnforcer
    private lateinit var engine: BlockingEngine

    @Before
    fun setUp() {
        clock = 1_000_000L
        store = FakeStore()
        enforcer = FakeEnforcer()
        engine = BlockingEngine(
            store, enforcer, now = { clock },
            waitMs = 20 * minute, readyWindowMs = 5 * minute,
        )
    }

    private fun assertRejected(o: Outcome) = assertTrue("expected Rejected but was $o", o is Outcome.Rejected)

    @Test fun startsUnblocked() {
        assertEquals(Status.UNBLOCKED, engine.state().status)
    }

    @Test fun blockEnforcesAndLocks() {
        assertEquals(Outcome.Ok, engine.block())
        assertEquals(Status.BLOCKED, engine.state().status)
        assertTrue(enforcer.enforced)
    }

    @Test fun cannotBlockWhenNotDeviceOwner() {
        enforcer.available = false
        assertRejected(engine.block())
        assertEquals(Status.UNBLOCKED, engine.state().status)
    }

    @Test fun wrongPhraseDoesNotStartTheWait() {
        engine.block()
        assertRejected(engine.requestUnlock("i declare on my honor"))
        assertEquals(Status.BLOCKED, engine.state().status)
    }

    @Test fun correctPhraseStartsTheWait() {
        engine.block()
        assertEquals(Outcome.Ok, engine.requestUnlock(UnlockPhrase.TEXT))
        val s = engine.state()
        assertEquals(Status.WAITING, s.status)
        assertEquals(20 * minute, s.remainingMs)
    }

    @Test fun cannotRequestUnlockWhenNotBlocked() {
        assertRejected(engine.requestUnlock(UnlockPhrase.TEXT))
    }

    @Test fun cannotConfirmBeforeWaitIsOver() {
        engine.block(); engine.requestUnlock(UnlockPhrase.TEXT)
        clock += 19 * minute + 59_000
        assertEquals(Status.WAITING, engine.state().status)
        assertRejected(engine.confirmUnlock(UnlockPhrase.TEXT))
        assertTrue(enforcer.enforced)
    }

    @Test fun readyAfterWait() {
        engine.block(); engine.requestUnlock(UnlockPhrase.TEXT)
        clock += 20 * minute
        assertEquals(Status.READY, engine.state().status)
    }

    @Test fun readyWindowCountsDownFromFiveMinutes() {
        engine.block(); engine.requestUnlock(UnlockPhrase.TEXT)
        clock += 20 * minute
        assertEquals(5 * minute, engine.state().remainingMs)
        clock += 2 * minute
        assertEquals(3 * minute, engine.state().remainingMs)
    }

    @Test fun lastSecondOfTheWindowStillWorks() {
        engine.block(); engine.requestUnlock(UnlockPhrase.TEXT)
        clock += 25 * minute - 1000
        assertEquals(Status.READY, engine.state().status)
        assertEquals(Outcome.Ok, engine.confirmUnlock(UnlockPhrase.TEXT))
        assertEquals(Status.UNBLOCKED, engine.state().status)
    }

    @Test fun requestExpiresAfterTheFiveMinuteWindow() {
        engine.block(); engine.requestUnlock(UnlockPhrase.TEXT)
        clock += 25 * minute
        assertEquals(Status.BLOCKED, engine.state().status)
        assertRejected(engine.confirmUnlock(UnlockPhrase.TEXT))
        assertTrue(enforcer.enforced)
    }

    @Test fun expiredRequestNeedsAFreshTwentyMinuteWait() {
        engine.block(); engine.requestUnlock(UnlockPhrase.TEXT)
        clock += 3 * 60 * minute // long after expiry
        assertEquals(Status.BLOCKED, engine.state().status)
        assertEquals(Outcome.Ok, engine.requestUnlock(UnlockPhrase.TEXT))
        val s = engine.state()
        assertEquals(Status.WAITING, s.status)
        assertEquals(20 * minute, s.remainingMs)
        assertRejected(engine.confirmUnlock(UnlockPhrase.TEXT))
    }

    @Test fun wrongPhraseAfterWaitStaysBlocked() {
        engine.block(); engine.requestUnlock(UnlockPhrase.TEXT)
        clock += 21 * minute
        assertRejected(engine.confirmUnlock("nope"))
        assertEquals(Status.READY, engine.state().status)
        assertTrue(enforcer.enforced)
    }

    @Test fun fullUnlockFlowReleasesEnforcer() {
        engine.block(); engine.requestUnlock(UnlockPhrase.TEXT)
        clock += 20 * minute
        assertEquals(Outcome.Ok, engine.confirmUnlock(UnlockPhrase.TEXT))
        assertEquals(Status.UNBLOCKED, engine.state().status)
        assertFalse(enforcer.enforced)
    }

    @Test fun clockMovedBackwardsNeverShortensTheWait() {
        engine.block(); engine.requestUnlock(UnlockPhrase.TEXT)
        clock -= 5 * minute
        val s = engine.state()
        assertEquals(Status.WAITING, s.status)
        assertEquals(20 * minute, s.remainingMs)
    }

    @Test fun clockMovedForwardsDoesSkipTheWait_whichIsWhyTheClockIsLocked() {
        engine.block(); engine.requestUnlock(UnlockPhrase.TEXT)
        clock += 21 * minute // someone moves the clock forward
        assertEquals(Status.READY, engine.state().status)
    }

    @Test fun cancelReturnsToBlockedAndNeedsANewWait() {
        engine.block(); engine.requestUnlock(UnlockPhrase.TEXT)
        clock += 22 * minute
        assertEquals(Outcome.Ok, engine.cancelUnlock())
        assertEquals(Status.BLOCKED, engine.state().status)
        engine.requestUnlock(UnlockPhrase.TEXT)
        assertEquals(Status.WAITING, engine.state().status)
    }

    @Test fun failedReleaseKeepsYouBlocked() {
        engine.block(); engine.requestUnlock(UnlockPhrase.TEXT)
        clock += 20 * minute
        enforcer.failOnRelease = true
        assertRejected(engine.confirmUnlock(UnlockPhrase.TEXT))
        assertEquals(Status.READY, engine.state().status)
    }

    @Test fun reconcileReappliesBlockWhenLocked() {
        engine.block()
        enforcer.enforced = false
        engine.reconcile()
        assertTrue(enforcer.enforced)
    }

    @Test fun reconcileDoesNothingWhenUnblocked() {
        engine.reconcile()
        assertFalse(enforcer.enforced)
    }

    @Test fun phraseIgnoresOuterSpacesButNothingElse() {
        assertTrue(UnlockPhrase.matches("  ${UnlockPhrase.TEXT}  "))
        assertFalse(UnlockPhrase.matches(UnlockPhrase.TEXT.lowercase()))
        assertFalse(UnlockPhrase.matches(UnlockPhrase.TEXT.dropLast(1)))
    }
}
