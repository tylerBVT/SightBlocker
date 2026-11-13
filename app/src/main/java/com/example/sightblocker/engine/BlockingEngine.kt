package com.example.sightblocker.engine

import java.util.concurrent.TimeUnit

/**
 * The rules. All the "can I unblock now?" logic lives here and nowhere else.
 * The screen only displays [state] and calls the functions below.
 *
 *   UNBLOCKED --block()--> BLOCKED --requestUnlock(phrase)--> WAITING --(20 min)--> READY
 *   READY --confirmUnlock(phrase)--> UNBLOCKED
 *   READY --(5 min pass without confirming)--> BLOCKED   (request expires)
 *   WAITING / READY --cancelUnlock()--> BLOCKED
 */
class BlockingEngine(
    private val store: BlockStore,
    private val enforcer: Enforcer,
    private val now: () -> Long = System::currentTimeMillis,
    private val waitMs: Long = DEFAULT_WAIT_MS,
    private val readyWindowMs: Long = DEFAULT_READY_WINDOW_MS,
) {

    /**
     * WAITING for [waitMs], then READY for [readyWindowMs] (remainingMs = time left to confirm).
     * If the window passes without confirming, the request expires and we are plain BLOCKED again.
     */
    fun state(): BlockState {
        if (!store.locked) return BlockState(Status.UNBLOCKED)
        val requestedAt = store.unlockRequestedAt
        if (requestedAt == 0L) return BlockState(Status.BLOCKED)
        // If the clock ever moves backwards, count it as zero time passed, never as extra.
        val elapsed = (now() - requestedAt).coerceAtLeast(0L)
        return when {
            elapsed < waitMs -> BlockState(Status.WAITING, remainingMs = waitMs - elapsed)
            elapsed < waitMs + readyWindowMs ->
                BlockState(Status.READY, remainingMs = waitMs + readyWindowMs - elapsed)
            else -> BlockState(Status.BLOCKED) // expired: a new request is needed
        }
    }

    fun block(): Outcome {
        if (!enforcer.isAvailable()) return Outcome.Rejected("Not Device Owner. See README.")
        if (state().status != Status.UNBLOCKED) return Outcome.Rejected("Already blocked.")
        try {
            enforcer.enforce()
        } catch (e: Exception) {
            return Outcome.Rejected("Could not block: ${e.message}")
        }
        store.unlockRequestedAt = 0L
        store.locked = true
        return Outcome.Ok
    }

    fun requestUnlock(typed: String): Outcome {
        if (state().status != Status.BLOCKED) return Outcome.Rejected("An unblock is already in progress.")
        if (!UnlockPhrase.matches(typed)) return Outcome.Rejected("The phrase does not match.")
        store.unlockRequestedAt = now()
        return Outcome.Ok
    }

    fun cancelUnlock(): Outcome {
        val status = state().status
        if (status != Status.WAITING && status != Status.READY) return Outcome.Rejected("Nothing to cancel.")
        store.unlockRequestedAt = 0L
        return Outcome.Ok
    }

    fun confirmUnlock(typed: String): Outcome {
        if (state().status != Status.READY) return Outcome.Rejected("The wait is not over yet.")
        if (!UnlockPhrase.matches(typed)) return Outcome.Rejected("The phrase does not match.")
        try {
            enforcer.release()
        } catch (e: Exception) {
            return Outcome.Rejected("Could not unblock: ${e.message}")
        }
        store.locked = false
        store.unlockRequestedAt = 0L
        return Outcome.Ok
    }

    /** Re-applies the block if we are supposed to be blocked. Call when the screen opens. */
    fun reconcile() {
        if (store.locked && enforcer.isAvailable()) {
            try {
                enforcer.enforce()
            } catch (_: Exception) {
                // Nothing useful to do here; the screen shows the real state.
            }
        }
    }

    companion object {
        val DEFAULT_WAIT_MS: Long = TimeUnit.MINUTES.toMillis(20)
        val DEFAULT_READY_WINDOW_MS: Long = TimeUnit.MINUTES.toMillis(5)
    }
}
