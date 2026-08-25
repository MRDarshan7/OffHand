package com.offhand.action

import com.offhand.data.ActionState
import com.offhand.parse.ActionType

sealed interface ConfirmResult {
    /** Offline action executed on the spot. */
    data object Executed : ConfirmResult

    /** Network action parked durably; dispatch happens when connected. */
    data object Queued : ConfirmResult

    data class Failed(val message: String) : ConfirmResult
}

/**
 * Confirm-time routing. Offline-capable actions execute immediately and never
 * queue; network actions land in the outbox as QUEUED. The parser/validator
 * layer never reaches this class — only a human tap does.
 */
class ActionCoordinator(
    private val repository: ActionRepository,
    private val executors: Map<ActionType, ActionExecutor>,
    /** Hook for the dispatcher (M4): called after an action becomes QUEUED. */
    private val onQueued: suspend (actionId: String) -> Unit = {},
) {

    private val offlineTypes =
        setOf(ActionType.CREATE_EVENT, ActionType.SET_REMINDER, ActionType.CAPTURE_NOTE)

    suspend fun confirm(actionId: String): ConfirmResult {
        val entity = requireNotNull(repository.getById(actionId)) { "No action $actionId" }
        val type = ActionType.valueOf(entity.type.uppercase())
        repository.transition(actionId, ActionState.CONFIRMED)

        return if (type in offlineTypes) {
            repository.transition(actionId, ActionState.SENDING)
            val slots = StoredSlots.decode(entity.slotsJson)
            when (val outcome = executors.getValue(type).execute(entity, slots)) {
                ExecOutcome.Success -> {
                    repository.transition(actionId, ActionState.DONE)
                    ConfirmResult.Executed
                }
                is ExecOutcome.Failure -> {
                    repository.transition(actionId, ActionState.FAILED, outcome.message)
                    ConfirmResult.Failed(outcome.message)
                }
            }
        } else {
            repository.transition(actionId, ActionState.QUEUED)
            onQueued(actionId)
            ConfirmResult.Queued
        }
    }

    suspend fun cancel(actionId: String) {
        repository.transition(actionId, ActionState.CANCELLED)
    }
}
