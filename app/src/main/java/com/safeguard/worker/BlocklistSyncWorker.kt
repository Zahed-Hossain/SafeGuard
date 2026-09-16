package com.safeguard.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.safeguard.SafeGuardApplication
import com.safeguard.blocklist.repository.UpdateResult
import java.util.concurrent.TimeUnit

/**
 * BlocklistSyncWorker manages periodic background blocklist updates via WorkManager.
 *
 * Requirements:
 * - Operates safely in the background.
 * - Adheres strictly to offline / battery / network constraints.
 * - Preserves last known good database if remote update fails or device is offline.
 */
class BlocklistSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "BlocklistSyncWorker"
        const val WORK_NAME = "safeguard_periodic_blocklist_sync"

        /**
         * Schedules periodic daily blocklist update according to Android WorkManager limitations.
         */
        fun schedulePeriodicUpdate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<BlocklistSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
            Log.i(TAG, "Scheduled periodic 24-hour blocklist sync")
        }

        /**
         * Cancels periodic background updates.
         */
        fun cancelPeriodicUpdate(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Cancelled periodic blocklist sync")
        }

        /**
         * Enqueues a one-time immediate blocklist sync check.
         */
        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<BlocklistSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeRequest)
            Log.i(TAG, "Triggered one-time immediate blocklist sync")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Executing SafeGuard background blocklist sync worker...")
        return try {
            val repository = SafeGuardApplication.instance.blocklistRepository
            val result = repository.updateFromRemote()

            when (result) {
                is UpdateResult.Success -> {
                    Log.i(TAG, "Blocklist update succeeded: version=${result.version}, domains=${result.domainCount}")
                    Result.success()
                }
                is UpdateResult.Failure -> {
                    Log.w(TAG, "Blocklist update did not complete: ${result.errorMessage}. Preserving last known good database.")
                    // If network temporary issue or placeholder URL, finish safely without corrupting local data
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during background blocklist sync: ${e.message}", e)
            Result.retry()
        }
    }
}
