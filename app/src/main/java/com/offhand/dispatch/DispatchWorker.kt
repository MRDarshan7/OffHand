package com.offhand.dispatch

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.offhand.OffhandApp
import com.offhand.action.ExecOutcome
import com.offhand.action.MAX_ATTEMPTS
import com.offhand.action.StoredSlots
import com.offhand.data.ActionState
import com.offhand.parse.ActionType

/**
 * Sends one queued action. Send-once is best-effort: the unique work name
 * plus the QUEUED state check below. A crash between the provider accepting
 * the message and the DONE write can still duplicate — documented, not hidden.
 */
class DispatchWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? OffhandApp ?: return Result.failure()
        val repository = app.container.actionRepository
        val actionId = inputData.getString(KEY_ACTION_ID) ?: return Result.success()

        val entity = repository.getById(actionId) ?: return Result.success()
        if (entity.state != ActionState.QUEUED.name) return Result.success()

        repository.transition(actionId, ActionState.SENDING)
        val type = ActionType.valueOf(entity.type.uppercase())
        val executor = app.container.networkExecutors[type]
        val outcome = executor?.execute(entity, StoredSlots.decode(entity.slotsJson))
            ?: ExecOutcome.Failure("No executor for ${entity.type}")

        return when (outcome) {
            ExecOutcome.Success -> {
                repository.transition(actionId, ActionState.DONE)
                Result.success()
            }

            is ExecOutcome.Failure -> {
                val failed = repository.transition(actionId, ActionState.FAILED, outcome.message)
                if (failed.attempts >= MAX_ATTEMPTS) {
                    // Terminal: surfaced in the Outbox as "Needs attention".
                    Result.failure()
                } else {
                    repository.transition(actionId, ActionState.QUEUED)
                    Result.retry()
                }
            }
        }
    }

    companion object {
        const val KEY_ACTION_ID = "action_id"
    }
}
