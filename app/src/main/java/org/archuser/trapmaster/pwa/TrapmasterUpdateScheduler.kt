package org.archuser.trapmaster.pwa

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val UPDATE_WORK_NAME = "trapmaster-pwa-updates"

object TrapmasterUpdateScheduler {

    fun schedule(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<TrapmasterUpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                UPDATE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
    }
}
