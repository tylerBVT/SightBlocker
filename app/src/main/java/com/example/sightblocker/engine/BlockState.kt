package com.example.sightblocker.engine

enum class Status {
    /** Nothing is blocked. */
    UNBLOCKED,

    /** Blocked, and no unblock has been requested. */
    BLOCKED,

    /** Blocked. An unblock was requested and the wait is running. */
    WAITING,

    /** Blocked. The wait is over, the phrase must be typed again to unblock. */
    READY,
}

data class BlockState(val status: Status, val remainingMs: Long = 0)

/** Result of asking the engine to do something. */
sealed interface Outcome {
    data object Ok : Outcome
    data class Rejected(val reason: String) : Outcome
}
