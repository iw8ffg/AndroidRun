package com.androidrun.autostartblocker.worker

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.androidrun.autostartblocker.data.repository.AppRepository
import com.androidrun.autostartblocker.util.NotificationHelper
import com.androidrun.autostartblocker.util.PackageHelper
import kotlinx.coroutines.delay

class AppKillerWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "AppKillerWorker"
        private const val NOTIFICATION_ID = 1001
        private const val KILL_ROUNDS = 3
        private const val ROUND_DELAY_MS = 10_000L
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationHelper.buildServiceNotification(
            applicationContext, "Blocking auto-starting apps..."
        )
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result {
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.w(TAG, "Could not set foreground info", e)
        }

        val repository = AppRepository.getInstance(applicationContext)
        val blockedPackages = try {
            repository.getBlockedPackageNames()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read blocked apps", e)
            return Result.failure()
        }

        if (blockedPackages.isEmpty()) {
            Log.i(TAG, "No blocked apps configured")
            return Result.success()
        }

        val am = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val killedPackages = mutableSetOf<String>()

        for (round in 1..KILL_ROUNDS) {
            Log.d(TAG, "Kill round $round/$KILL_ROUNDS")
            for (pkg in blockedPackages) {
                try {
                    am.killBackgroundProcesses(pkg)
                    if (!PackageHelper.isAppRunning(applicationContext, pkg)) {
                        killedPackages.add(pkg)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error killing $pkg", e)
                }
            }
            if (round < KILL_ROUNDS) {
                delay(ROUND_DELAY_MS)
            }
        }

        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm?.notify(
            NotificationHelper.COMPLETION_NOTIFICATION_ID,
            NotificationHelper.buildCompletionNotification(applicationContext, killedPackages.size)
        )

        Log.i(TAG, "Kill cycle complete. Killed ${killedPackages.size} app(s)")
        return Result.success()
    }
}
