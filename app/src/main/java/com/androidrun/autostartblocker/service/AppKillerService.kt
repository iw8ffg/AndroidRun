package com.androidrun.autostartblocker.service

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.androidrun.autostartblocker.data.repository.AppRepository
import com.androidrun.autostartblocker.util.NotificationHelper
import com.androidrun.autostartblocker.util.PackageHelper
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
        private const val NOTIFICATION_ID_SERVICE = 1001
        private const val NOTIFICATION_ID_COMPLETION = 1002
        private const val KILL_ROUNDS = 3
        private const val ROUND_DELAY_MS = 10_000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var killJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationHelper.buildServiceNotification(
            this, "Blocking auto-starting apps..."
        )
        startForeground(NOTIFICATION_ID_SERVICE, notification)

        if (killJob?.isActive != true) {
            killJob = serviceScope.launch { performKillCycle() }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun performKillCycle() {
        val repository = AppRepository.getInstance(applicationContext)
        val blockedPackages = try {
            repository.getBlockedPackageNames()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read blocked apps", e)
            emptyList()
        }

        if (blockedPackages.isEmpty()) {
            Log.i(TAG, "No blocked apps configured")
            postCompletionAndStop(0)
            return
        }

        Log.i(TAG, "Starting kill cycle for ${blockedPackages.size} blocked app(s)")
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val killedPackages = mutableSetOf<String>()

        for (round in 1..KILL_ROUNDS) {
            Log.d(TAG, "Kill round $round/$KILL_ROUNDS")

            val progressNotification = NotificationHelper.buildServiceNotification(
                this@AppKillerService,
                "Kill round $round/$KILL_ROUNDS..."
            )
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID_SERVICE, progressNotification)

            for (pkg in blockedPackages) {
                try {
                    am.killBackgroundProcesses(pkg)
                    if (!PackageHelper.isAppRunning(this@AppKillerService, pkg)) {
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

        Log.i(TAG, "Kill cycle complete. Killed ${killedPackages.size} app(s)")
        postCompletionAndStop(killedPackages.size)
    }

    private fun postCompletionAndStop(killedCount: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(
            NOTIFICATION_ID_COMPLETION,
            NotificationHelper.buildCompletionNotification(this, killedCount)
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
