package com.offhand.dispatch

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.offhand.action.ActionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Idempotent enqueueing: unique work per action UUID (KEEP policy) with a
 * NETWORK_CONNECTED constraint plus exponential backoff. The connectivity
 * callback calls [drainQueued] for an immediate drain the moment a network
 * comes back.
 */
class Dispatcher(
    private val context: Context,
    private val repository: ActionRepository,
    private val scope: CoroutineScope,
) {

    fun enqueue(actionId: String) {
        val request = OneTimeWorkRequestBuilder<DispatchWorker>()
            .setInputData(workDataOf(DispatchWorker.KEY_ACTION_ID to actionId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "action-$actionId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun drainQueued() {
        scope.launch {
            repository.queued().forEach { enqueue(it.id) }
        }
    }
}
