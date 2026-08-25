package com.offhand.action

import com.offhand.data.ActionDao
import com.offhand.data.ActionEntity
import com.offhand.data.ActionState
import com.offhand.data.ActionState.CANCELLED
import com.offhand.data.ActionState.CONFIRMED
import com.offhand.data.ActionState.DONE
import com.offhand.data.ActionState.DRAFT
import com.offhand.data.ActionState.FAILED
import com.offhand.data.ActionState.QUEUED
import com.offhand.data.ActionState.SENDING
import com.offhand.parse.ActionType
import java.util.UUID

/** Max attempts before FAILED becomes terminal ("Needs attention"). */
const val MAX_ATTEMPTS = 5

/**
 * The single gate for state transitions. Every move goes through
 * [transition]; illegal moves throw. Send-once is best-effort: unique
 * WorkManager work per action id plus a state check before the send —
 * a crash in the window between provider-accept and DB-write can still
 * duplicate, which is documented, not hidden.
 */
class ActionRepository(private val dao: ActionDao) {

    private val legalMoves: Map<ActionState, Set<ActionState>> = mapOf(
        DRAFT to setOf(CONFIRMED, CANCELLED),
        CONFIRMED to setOf(QUEUED, SENDING, CANCELLED),
        QUEUED to setOf(SENDING, CANCELLED),
        SENDING to setOf(DONE, FAILED),
        FAILED to setOf(QUEUED),
        DONE to emptySet(),
        CANCELLED to emptySet(),
    )

    class IllegalTransition(from: String, to: ActionState) :
        IllegalStateException("Illegal action state transition $from -> $to")

    suspend fun createDraft(type: ActionType, slots: StoredSlots, transcript: String): ActionEntity {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val entity = ActionEntity(
            id = id,
            type = type.wireName,
            slotsJson = slots.encode(),
            state = DRAFT.name,
            createdAt = now,
            updatedAt = now,
            transcript = transcript,
            idempotencyKey = id,
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun updateSlots(id: String, slots: StoredSlots) {
        val entity = requireNotNull(dao.getById(id)) { "No action $id" }
        check(entity.state == DRAFT.name) { "Slots only editable in DRAFT" }
        dao.upsert(entity.copy(slotsJson = slots.encode(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun transition(id: String, to: ActionState, error: String? = null): ActionEntity {
        val entity = requireNotNull(dao.getById(id)) { "No action $id" }
        val from = ActionState.valueOf(entity.state)
        if (to !in legalMoves.getValue(from)) throw IllegalTransition(entity.state, to)
        if (from == FAILED && to == QUEUED && entity.attempts >= MAX_ATTEMPTS) {
            throw IllegalTransition("FAILED(terminal, ${entity.attempts} attempts)", to)
        }
        val updated = entity.copy(
            state = to.name,
            updatedAt = System.currentTimeMillis(),
            attempts = if (to == FAILED) entity.attempts + 1 else entity.attempts,
            lastError = if (to == FAILED) error else entity.lastError,
        )
        dao.upsert(updated)
        return updated
    }

    suspend fun getById(id: String): ActionEntity? = dao.getById(id)

    suspend fun queued(): List<ActionEntity> = dao.getByState(QUEUED.name)

    /**
     * Startup recovery: a SENDING item with no recorded result goes back to
     * QUEUED so the dispatcher retries it.
     */
    suspend fun recoverInterruptedSends() {
        dao.getByState(SENDING.name).forEach { entity ->
            dao.upsert(
                entity.copy(state = QUEUED.name, updatedAt = System.currentTimeMillis()),
            )
        }
    }

    /** Pure legality check, exposed for tests. */
    fun isLegal(from: ActionState, to: ActionState): Boolean =
        to in legalMoves.getValue(from)
}
