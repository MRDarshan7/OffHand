package com.offhand.data

/**
 * Lifecycle of an action. Legal transitions (enforced by the repository, M3):
 *   DRAFT -> CONFIRMED -> QUEUED -> SENDING -> DONE
 *   DRAFT/CONFIRMED/QUEUED -> CANCELLED
 *   SENDING -> FAILED -> QUEUED (retry, attempts + 1)
 *   FAILED with attempts >= 5 is terminal ("Needs attention").
 */
enum class ActionState {
    DRAFT, CONFIRMED, QUEUED, SENDING, DONE, CANCELLED, FAILED
}
