package com.androidrun.autostartblocker.service

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.androidrun.autostartblocker.data.repository.AppRepository
import com.androidrun.autostartblocker.util.NotificationHelper
import com.androidrun.autostartblocker.worker.AppKillerWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppKillerService : Service() {

    companion object {
        private const val TAG = "AppKillerService"
        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        private const val NOTIFICATION_ID_SERVICE = 1001
        private const val NOTIFICATION_ID_COMPLETION = 1002
        private const val KILL_ROUNDS = 6
        private val ROUND_DELAYS_MS = longArrayOf(2_000, 5_000, 5_000, 10_000, 15_000, 0)
        private const val WAKE_LOCK_TIMEOUT_MS = 120_000L

        fun scheduleOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<AppKillerWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "app_killer_oneshot",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var killJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)

        // Acquire wake lock to prevent device sleeping during kill cycle
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AutoStartBlocker::KillCycle"
        ).apply {
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground immediately (within 5s on Android 12+)
        val notification = NotificationHelper.buildServiceNotification(
            this, "Blocking auto-starting apps..."
        )
        startForeground(NOTIFICATION_ID_SERVICE, notification)

        val targetPackage = intent?.getStringExtra(EXTRA_TARGET_PACKAGE)

        if (killJob?.isActive != true) {
            killJob = serviceScope.launch {
                performKillCycle(targetPackage)
            }
        }

        // START_STICKY: restart service if killed by system
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) { }
        wakeLock = null
    }

    private suspend fun performKillCycle(targetPackage: String? = null) {
        val repository = try {
            AppRepository.getInstance(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get repository, retrying in 3s...", e)
            delay(3_000)
            try {
                AppRepository.getInstance(applicationContext)
            } catch (e2: Exception) {
                Log.e(TAG, "Repository still unavailable, giving up", e2)
                postCompletionAndStop(0)
                return
            }
        }

        val blockedPackages = try {
            if (targetPackage != null) {
                // Single target: only kill if it's in the blocked list
                val isBlocked = repository.isBlocked(targetPackage)
                if (isBlocked) listOf(targetPackage) else emptyList()
            } else {
                repository.getBlockedPackageNames()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read blocked apps", e)
            emptyList()
        }

        if (blockedPackages.isEmpty()) {
            Log.i(TAG, "No blocked apps to kill")
            postCompletionAndStop(0)
            return
        }

        Log.i(TAG, "Starting kill cycle for ${blockedPackages.size} app(s): $blockedPackages")
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val killedPackages = mutableSetOf<String>()

        for (round in 0 until KILL_ROUNDS) {
            Log.d(TAG, "Kill round ${round + 1}/$KILL_ROUNDS")

            val progressNotification = NotificationHelper.buildServiceNotification(
                this@AppKillerService,
                "Blocking apps — round ${round + 1}/$KILL_ROUNDS"
            )
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID_SERVICE, progressNotification)

            for (pkg in blockedPackages) {
                try {
                    am.killBackgroundProcesses(pkg)
                    killedPackages.add(pkg)
                    Log.d(TAG, "  killBackgroundProcesses($pkg)")
                } catch (e: Exception) {
                    Log.e(TAG, "  Error killing $pkg", e)
                }
            }

            val delayMs = ROUND_DELAYS_MS[round]
            if (delayMs > 0) {
                delay(delayMs)
            }
        }

        Log.i(TAG, "Kill cycle complete. Processed ${killedPackages.size} app(s)")
        postCompletionAndStop(killedPackages.size)
    }

    private fun postCompletionAndStop(killedCount: Int) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(
                NOTIFICATION_ID_COMPLETION,
                NotificationHelper.buildCompletionNotification(this, killedCount)
            )
        } catch (_: Exception) { }

        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
