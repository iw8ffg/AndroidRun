package com.androidrun.autostartblocker.worker

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.androidrun.autostartblocker.data.repository.AppRepository
import com.androidrun.autostartblocker.util.NotificationHelper
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class AppKillerWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "AppKillerWorker"
        private const val NOTIFICATION_ID = 1001
        private const val KILL_ROUNDS = 4
        private val ROUND_DELAYS_MS = longArrayOf(2_000, 5_000, 10_000, 0)
        private const val PERIODIC_WORK_NAME = "app_killer_periodic"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<AppKillerWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(TAG, "Periodic kill work scheduled (every 15 min)")
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            Log.i(TAG, "Periodic kill work cancelled")
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        NotificationHelper.createNotificationChannel(applicationContext)
        val notification = NotificationHelper.buildServiceNotification(
            applicationContext, "Blocking auto-starting apps..."
        )
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result {
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.w(TAG, "Could not set foreground info — running in background", e)
        }

        val repository = try {
            AppRepository.getInstance(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get repository", e)
            return Result.retry()
        }

        val blockedPackages = try {
            repository.getBlockedPackageNames()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read blocked apps", e)
            return Result.retry()
        }

        if (blockedPackages.isEmpty()) {
            Log.i(TAG, "No blocked apps configured")
            return Result.success()
        }

        val am = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val killedPackages = mutableSetOf<String>()

        for (round in 0 until KILL_ROUNDS) {
            Log.d(TAG, "Kill round ${round + 1}/$KILL_ROUNDS")
            for (pkg in blockedPackages) {
                try {
                    am.killBackgroundProcesses(pkg)
                    killedPackages.add(pkg)
                } catch (e: Exception) {
                    Log.e(TAG, "Error killing $pkg", e)
                }
            }
            val delayMs = ROUND_DELAYS_MS[round]
            if (delayMs > 0) delay(delayMs)
        }

        try {
            val nm = applicationContext.getSystemService(NotificationManager::class.java)
            nm?.notify(
                NotificationHelper.COMPLETION_NOTIFICATION_ID,
                NotificationHelper.buildCompletionNotification(applicationContext, killedPackages.size)
            )
        } catch (_: Exception) { }

        Log.i(TAG, "Kill cycle complete. Processed ${killedPackages.size} app(s)")
        return Result.success()
    }
}
