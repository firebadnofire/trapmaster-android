package org.archuser.trapmaster.pwa

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class TrapmasterUpdateWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : Worker(appContext, workerParameters) {

    override fun doWork(): Result {
        return when (val outcome = TrapmasterUpstreamUpdater(applicationContext).updateIfNeeded(force = true)) {
            is TrapmasterUpdateOutcome.Failed -> {
                Log.w("TrapmasterUpdate", "Periodic update failed: ${outcome.message}", outcome.cause)
                Result.success()
            }

            else -> Result.success()
        }
    }
}
