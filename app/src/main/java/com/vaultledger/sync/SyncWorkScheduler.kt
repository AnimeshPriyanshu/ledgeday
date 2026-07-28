package com.vaultledger.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncWorkScheduler {
    private const val TAG = "SyncWorkScheduler"
    internal const val WORK_NAME = "vault_ledger_periodic_sync"

    fun schedule(context: Context) {
        Log.d(TAG, "Scheduling periodic sync work")
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val syncRequest =
            PeriodicWorkRequestBuilder<SyncWorker>(
                15,
                TimeUnit.MINUTES,
            ).setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest,
        )
    }

    fun cancel(context: Context) {
        Log.d(TAG, "Cancelling periodic sync work")
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
